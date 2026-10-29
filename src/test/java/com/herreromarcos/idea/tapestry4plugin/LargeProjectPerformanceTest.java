package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Leistungs- und Lasttest mit einem großen Tapestry-Projekt (Standard: 3000 Seiten, 300 Komponenten, 5 Bibliotheken
 * mit je 20 Komponenten). Läuft nur auf Anfrage: {@code gradlew test --tests *LargeProjectPerformanceTest -Pperformance}
 * (optional {@code -Pperformance.pages=10000}). Die Messwerte stehen in {@code build/reports/performance.txt}.
 * <p>
 * Das Projekt ist bewusst nicht trivial: Seitenklassen erben über Modul- und Anwendungs-Basisklassen, nutzen
 * Tapestry-4.1-Annotationen und ein Application State Object; die Templates enthalten Schleifen, Bedingungen,
 * Formulare und OGNL-Ketten über ein Domänenmodell mit Vererbung und Listen; Komponenten verwenden sich
 * gegenseitig bis zur Tiefe 3; jede Seite hat lokalisierte Messages. Jede hundertste Seite enthält Fehler, alle
 * anderen sind fehlerfrei.
 */
public class LargeProjectPerformanceTest extends TapestryTestCase {
    private static final String ANNOTATIONS = "org.apache.tapestry.annotations.";
    private static final int PAGES = Integer.getInteger("tapestry.performance.pages", 3000);
    private static final int PAGES_PER_MODULE = 100;
    private static final int MODULES = (PAGES + PAGES_PER_MODULE - 1) / PAGES_PER_MODULE;
    private static final int COMPONENTS = Math.max(10, PAGES / 10);
    private static final int COMPONENT_DEPTH = 3;
    private static final int LIBRARIES = 5;
    private static final int LIBRARY_COMPONENTS = 20;
    private static final int SECTIONS = 5;
    private static final int FAULTY_PAGE_INTERVAL = 100;
    private static final int TYPING_ITERATIONS = 100;
    private static final int SPEC_EDIT_ITERATIONS = 30;
    private static final int JAVA_EDIT_ITERATIONS = 30;
    private static final int FILE_CREATE_ITERATIONS = 30;

    /** Echtes JDK der Test-JVM: OGNL über Listen, java.lang-Klassen und Bean-Klassen aus java.util brauchen es. */
    private static final LightProjectDescriptor WITH_JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("test-jdk", System.getProperty("java.home"), false));

    private final List<String> report = new ArrayList<>();

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return WITH_JDK;
    }

    @Override
    protected boolean shouldRunTest() {
        return Boolean.getBoolean("tapestry.performance") && super.shouldRunTest();
    }

    public void testLargeProject() throws IOException {
        final long generation = millis(this::generateProject);
        line("Projekt: %d Seiten in %d Modulen, %d Komponenten (Tiefe %d), %d Bibliotheken – erzeugt in %d ms",
                PAGES, MODULES, COMPONENTS, COMPONENT_DEPTH, LIBRARIES, generation);

        final int page = PAGES / 2;
        final VirtualFile template = file(pagePath(page, "html"));
        final VirtualFile spec = file(pagePath(page, "page"));

        myFixture.configureFromExistingVirtualFile(template);
        line("Template, erste Hervorhebung (kalte Caches): %d ms", millis(() -> myFixture.doHighlighting()));
        problems("Template");
        measure("Template, erneute Hervorhebung", 20, () -> myFixture.doHighlighting());

        myFixture.configureFromExistingVirtualFile(spec);
        line("Spezifikation, erste Hervorhebung: %d ms", millis(() -> myFixture.doHighlighting()));
        problems("Spezifikation");
        measure("Spezifikation, erneute Hervorhebung", 20, () -> myFixture.doHighlighting());

        // Komponente mit der tiefsten Verschachtelung (verwendet zwei weitere Projektkomponenten und eine Bibliothek)
        final VirtualFile nested = file("WEB-INF/components/Comp%d.html".formatted(COMPONENT_DEPTH - 1));
        myFixture.configureFromExistingVirtualFile(nested);
        line("Verschachtelte Komponente, erste Hervorhebung: %d ms", millis(() -> myFixture.doHighlighting()));
        problems("Verschachtelte Komponente");
        measure("Verschachtelte Komponente, erneute Hervorhebung", 20, () -> myFixture.doHighlighting());

        myFixture.configureFromExistingVirtualFile(file(pagePath(faultyPage(page), "html")));
        line("Fehlerhaftes Template, erste Hervorhebung: %d ms", millis(() -> myFixture.doHighlighting()));
        problems("Fehlerhaftes Template");

        // Last: Tippen im Template (darf die projektweiten Caches nicht verwerfen)
        myFixture.configureFromExistingVirtualFile(template);
        final int templateText = myFixture.getFile().getText().indexOf("Text ") + "Text ".length();
        measure("Tippen im Template + Hervorhebung", TYPING_ITERATIONS, () -> {
            insert(templateText, "x");
            myFixture.doHighlighting();
        });

        // Last: Tippen in der Spezifikation (verwirft die projektweiten Caches), danach Template hervorheben
        measure("Tippen in Spezifikation + Template hervorheben", SPEC_EDIT_ITERATIONS, () -> {
            myFixture.configureFromExistingVirtualFile(spec);
            insert(myFixture.getFile().getText().indexOf("<description>") + "<description>".length(), "x");
            myFixture.configureFromExistingVirtualFile(template);
            myFixture.doHighlighting();
        });

        // Last: Tippen in der Seitenklasse (Java-Änderung), danach Template hervorheben
        final PsiClass pageClass = myFixture.findClass(pageClassName(page));
        final VirtualFile pageClassFile = pageClass.getContainingFile().getVirtualFile();
        measure("Tippen in Seitenklasse + Template hervorheben", JAVA_EDIT_ITERATIONS, () -> {
            myFixture.configureFromExistingVirtualFile(pageClassFile);
            insert(myFixture.getFile().getText().indexOf("// Notiz") + "// Notiz".length(), "x");
            myFixture.configureFromExistingVirtualFile(template);
            myFixture.doHighlighting();
        });

        // Last: beliebige Datei anlegen (Strukturänderung), danach Template hervorheben
        final int[] created = {0};
        measure("Datei anlegen + Template hervorheben", FILE_CREATE_ITERATIONS, () -> {
            create("scratch/note%d.txt".formatted(created[0]++), "x");
            myFixture.doHighlighting();
        });

        // Projektweite Verzeichnisse nach einer Strukturänderung (kalt): ein neues Template verwirft die Caches
        create("scratch/Trigger.html", "<html/>");
        line("Komponententypen neu berechnen: %d ms (%d Typen)", millis(() -> TapestryRegistry.getComponentTypes(getProject())),
                TapestryRegistry.getComponentTypes(getProject()).size());
        line("Seiten neu berechnen: %d ms (%d Einträge)", millis(() -> TapestryRegistry.getPages(getProject())),
                TapestryRegistry.getPages(getProject()).size());
        line("Spezifikationen zu einer Klasse (kalt): %d ms", millis(() -> TapestryModel.findSpecsForClass(pageClass)));

        // Completion
        myFixture.configureFromExistingVirtualFile(template);
        measure("Completion jwcid=\"@\"", 10, () -> complete("jwcid=\"@Insert\"", "jwcid=\"@".length()));
        measure("Completion ognl:user.", 10, () -> complete("ognl:user.address.city", "ognl:user.".length()));
        measure("Completion ognl:line.product. (geerbt, Schleife)", 10,
                () -> complete("ognl:line.product.category", "ognl:line.product.".length()));
        measure("Completion message:", 10, () -> complete("message:title", "message:".length()));

        // Java: Gutter-Icons und Annotationen der Seitenklasse
        myFixture.configureFromExistingVirtualFile(pageClassFile);
        measure("Seitenklasse hervorheben (Gutter, Annotationen)", 10, () -> myFixture.doHighlighting());
        problems("Seitenklasse");

        // Find Usages von Gettern, die in allen Templates per OGNL verwendet werden
        findUsages("com.example.domain.User", "getAddress");
        findUsages("com.example.domain.OrderLine", "getProduct");

        final Path out = Path.of(System.getProperty("user.dir"), "build", "reports", "performance.txt");
        Files.createDirectories(out.getParent());
        Files.write(out, report, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ Messung

    private void measure(String name, int iterations, Runnable action) {
        final long[] times = new long[iterations];
        for (int i = 0; i < iterations; i++) times[i] = millis(action);
        Arrays.sort(times);
        line("%s: Median %d ms, p95 %d ms, max %d ms (n=%d)", name, times[iterations / 2],
                times[Math.min(iterations - 1, (int) Math.ceil(iterations * 0.95) - 1)], times[iterations - 1], iterations);
    }

    private static long millis(Runnable action) {
        final long start = System.nanoTime();
        action.run();
        return (System.nanoTime() - start) / 1_000_000;
    }

    private void line(String format, Object... args) {
        final String text = String.format(Locale.ROOT, format, args);
        report.add(text);
        System.out.println("[performance] " + text);
    }

    /** Zählt Fehler und Warnungen in der geöffneten Datei; die einzelnen Meldungen nur auf der Konsole. */
    private void problems(String name) {
        final List<HighlightInfo> infos = myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity().compareTo(HighlightSeverity.WARNING) >= 0)
                .toList();
        final long errors = infos.stream().filter(info -> info.getSeverity().compareTo(HighlightSeverity.ERROR) >= 0).count();
        line("%s: %d Fehler, %d Warnungen", name, errors, infos.size() - errors);
        infos.forEach(info -> System.out.println("[performance]   %s: %s (%s)".formatted(info.getSeverity(), info.getDescription(),
                info.getText())));
    }

    private void findUsages(String className, String methodName) {
        final PsiMethod getter = myFixture.findClass(className).findMethodsByName(methodName, false)[0];
        final int[] usages = {0};
        line("Find Usages %s.%s (alle Templates): %d ms, %d Treffer", className.substring(className.lastIndexOf('.') + 1),
                methodName, millis(() -> usages[0] = myFixture.findUsages(getter).size()), usages[0]);
    }

    private void insert(int offset, String text) {
        final Document document = myFixture.getEditor().getDocument();
        WriteCommandAction.runWriteCommandAction(getProject(), () -> document.insertString(offset, text));
        PsiDocumentManager.getInstance(getProject()).commitDocument(document);
    }

    private void complete(String anchor, int caretInAnchor) {
        final int offset = myFixture.getFile().getText().indexOf(anchor);
        assertTrue(anchor, offset >= 0);
        myFixture.getEditor().getCaretModel().moveToOffset(offset + caretInAnchor);
        myFixture.completeBasic();
        if (myFixture.getLookup() != null) myFixture.getLookup().hideLookup(true);
    }

    private VirtualFile file(String path) {
        final VirtualFile file = myFixture.findFileInTempDir(path);
        assertNotNull(path, file);
        return file;
    }

    // ------------------------------------------------------------------ Projekt

    private static String module(int page) {
        return "module%d".formatted(page / PAGES_PER_MODULE);
    }

    private static String pagePath(int page, String extension) {
        return "WEB-INF/%s/Page%d.%s".formatted(module(page), page, extension);
    }

    private static String pageClassName(int page) {
        return "com.example.%s.Page%d".formatted(module(page), page);
    }

    private static boolean isFaulty(int page) {
        return page % FAULTY_PAGE_INTERVAL == FAULTY_PAGE_INTERVAL / 3;
    }

    /** Die nächste fehlerhafte Seite ab {@code page}. */
    private static int faultyPage(int page) {
        int faulty = page;
        while (!isFaulty(faulty)) faulty++;
        return faulty;
    }

    private void create(String path, String text) {
        try {
            myFixture.getTempDirFixture().createFile(path, text);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Ersetzt den Inhalt einer Datei aus dem Basis-Setup. */
    private void replace(String path, String text) {
        final VirtualFile file = file(path);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                VfsUtil.saveText(file, text);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private void generateProject() {
        generateFramework();
        generateDomain();
        generateApplication();
        generateLibraries();
        generateComponents();
        for (int m = 0; m < MODULES; m++) generateModule(m);
        for (int p = 0; p < PAGES; p++) generatePage(p);
        create("WEB-INF/logo.png", "");
    }

    /** Framework-Komponenten, die Templates realer Anwendungen typischerweise verwenden, und die Annotationen von 4.1. */
    private void generateFramework() {
        replace("org/apache/tapestry/Framework.library", """
                <library-specification>
                  <component-type type="Insert" specification-path="components/Insert.jwc"/>
                  <component-type type="DirectLink" specification-path="link/DirectLink.jwc"/>
                  <component-type type="PageLink" specification-path="link/PageLink.jwc"/>
                  <component-type type="For" specification-path="components/For.jwc"/>
                  <component-type type="If" specification-path="components/If.jwc"/>
                  <component-type type="Else" specification-path="components/Else.jwc"/>
                  <component-type type="Any" specification-path="components/Any.jwc"/>
                  <component-type type="RenderBody" specification-path="components/RenderBody.jwc"/>
                  <component-type type="Form" specification-path="form/Form.jwc"/>
                  <component-type type="TextField" specification-path="form/TextField.jwc"/>
                  <component-type type="Submit" specification-path="form/Submit.jwc"/>
                </library-specification>
                """);
        create("org/apache/tapestry/link/PageLink.jwc", """
                <component-specification>
                  <parameter name="page" required="yes"/>
                  <parameter name="disabled"/>
                </component-specification>
                """);
        create("org/apache/tapestry/components/For.jwc", """
                <component-specification>
                  <parameter name="source" required="yes"/>
                  <parameter name="value"/>
                  <parameter name="index"/>
                  <parameter name="element"/>
                  <parameter name="keyExpression"/>
                </component-specification>
                """);
        create("org/apache/tapestry/components/If.jwc", """
                <component-specification>
                  <parameter name="condition" required="yes"/>
                  <parameter name="element"/>
                </component-specification>
                """);
        create("org/apache/tapestry/components/Else.jwc", """
                <component-specification>
                  <parameter name="element"/>
                </component-specification>
                """);
        create("org/apache/tapestry/components/Any.jwc", """
                <component-specification>
                  <parameter name="element"/>
                </component-specification>
                """);
        create("org/apache/tapestry/components/RenderBody.jwc", """
                <component-specification allow-body="no" allow-informal-parameters="no"/>
                """);
        create("org/apache/tapestry/form/Form.jwc", """
                <component-specification>
                  <parameter name="listener"/>
                  <parameter name="success"/>
                  <parameter name="delegate"/>
                </component-specification>
                """);
        create("org/apache/tapestry/form/TextField.jwc", """
                <component-specification allow-body="no">
                  <parameter name="value" required="yes"/>
                  <parameter name="disabled"/>
                </component-specification>
                """);
        create("org/apache/tapestry/form/Submit.jwc", """
                <component-specification allow-body="no">
                  <parameter name="listener"/>
                  <parameter name="label"/>
                </component-specification>
                """);
        addAnnotation("InjectPage", "String value();");
        addAnnotation("InjectComponent", "String value();");
        addAnnotation("InjectState", "String value() default \"\";");
        addAnnotation("Message", "String value() default \"\";");
        addAnnotation("InitialValue", "String value();");
        addAnnotation("Component", "String id() default \"\"; String type() default \"\"; String copyOf() default \"\"; "
                + "String[] bindings() default {};");
        // Wie im Tapestry-JAR: hivemodule.xml bindet tapestry.state.xml als Sub-Modul ein
        create("META-INF/hivemodule.xml", """
                <module id="tapestry" version="4.0.0"><sub-module descriptor="tapestry.state.xml"/></module>
                """);
        create("META-INF/tapestry.state.xml", """
                <module id="tapestry.state" version="4.0.0">
                  <contribution configuration-id="FactoryObjects">
                    <state-object name="global" scope="application"/>
                    <state-object name="visit" scope="session"/>
                  </contribution>
                </module>
                """);
    }

    /** Domänenmodell mit gemeinsamer Basisklasse, Listen, Enum und rekursiver Kategorie. */
    private void generateDomain() {
        create("com/example/domain/Entity.java", """
                package com.example.domain;
                public abstract class Entity {
                    public Long getId() { return null; }
                    public int getVersion() { return 0; }
                }
                """);
        create("com/example/domain/Country.java", """
                package com.example.domain;
                public class Country extends Entity {
                    public String getName() { return null; }
                    public String getIsoCode() { return null; }
                }
                """);
        create("com/example/domain/Address.java", """
                package com.example.domain;
                public class Address extends Entity {
                    public String getCity() { return null; }
                    public String getStreet() { return null; }
                    public Country getCountry() { return null; }
                }
                """);
        create("com/example/domain/Category.java", """
                package com.example.domain;
                public class Category extends Entity {
                    public String getName() { return null; }
                    public Category getParent() { return null; }
                }
                """);
        create("com/example/domain/Product.java", """
                package com.example.domain;
                public class Product extends Entity {
                    public String getName() { return null; }
                    public double getPrice() { return 0; }
                    public Category getCategory() { return null; }
                }
                """);
        create("com/example/domain/OrderLine.java", """
                package com.example.domain;
                public class OrderLine extends Entity {
                    public Product getProduct() { return null; }
                    public int getQuantity() { return 0; }
                }
                """);
        create("com/example/domain/OrderStatus.java", """
                package com.example.domain;
                public enum OrderStatus { NEW, SHIPPED, CANCELLED }
                """);
        create("com/example/domain/Order.java", """
                package com.example.domain;
                public class Order extends Entity {
                    public Customer getCustomer() { return null; }
                    public java.util.List<OrderLine> getLines() { return null; }
                    public double getTotal() { return 0; }
                    public OrderStatus getStatus() { return null; }
                }
                """);
        create("com/example/domain/Customer.java", """
                package com.example.domain;
                public class Customer extends Entity {
                    public String getName() { return null; }
                    public java.util.List<Address> getAddresses() { return null; }
                    public java.util.List<Order> getOrders() { return null; }
                    public boolean isPremium() { return false; }
                }
                """);
        create("com/example/domain/User.java", """
                package com.example.domain;
                public class User extends Entity {
                    public Address getAddress() { return null; }
                    public String getName() { return null; }
                    public java.util.List<Address> getAddresses() { return null; }
                    public Customer getCustomer() { return null; }
                }
                """);
        create("com/example/domain/SessionData.java", """
                package com.example.domain;
                public class SessionData {
                    public String getLastSearch() { return null; }
                    public User getLoggedInUser() { return null; }
                }
                """);
    }

    /** Application-Spezifikation, globale Messages, eigenes ASO und gemeinsame Basisklasse aller Seiten. */
    private void generateApplication() {
        final StringBuilder libraries = new StringBuilder();
        for (int l = 0; l < LIBRARIES; l++) {
            libraries.append("  <library id=\"lib%d\" specification-path=\"/com/example/lib%d/Lib%d.library\"/>\n".formatted(l, l, l));
        }
        create("WEB-INF/app.application", DOCTYPE_4_0.formatted("application") + """
                <application name="app">
                  <meta key="org.apache.tapestry.bean-class-packages" value="java.util"/>
                %s</application>
                """.formatted(libraries));
        create("WEB-INF/app.properties", "global=Global\nsearch=Suchen\n");
        create("WEB-INF/app_de.properties", "global=Global\nsearch=Suchen\n");
        create("WEB-INF/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="tapestry.state.ApplicationObjects">
                    <state-object name="session-data" scope="session"/>
                  </contribution>
                </module>
                """);
        create("com/example/base/AppBasePage.java", """
                package com.example.base;
                public abstract class AppBasePage extends org.apache.tapestry.html.BasePage {
                    @%sInjectState("session-data")
                    public abstract com.example.domain.SessionData getSessionData();
                    public abstract com.example.domain.User getUser();
                    public boolean isLoggedIn() { return getSessionData().getLoggedInUser() != null; }
                    public void onSearch() {}
                }
                """.formatted(ANNOTATIONS));
    }

    private void generateLibraries() {
        for (int l = 0; l < LIBRARIES; l++) {
            final StringBuilder types = new StringBuilder();
            for (int c = 0; c < LIBRARY_COMPONENTS; c++) {
                types.append("  <component-type type=\"Widget%d\" specification-path=\"Widget%d.jwc\"/>\n".formatted(c, c));
                create("com/example/lib%d/Widget%d.jwc".formatted(l, c), component("""
                        <component-specification>
                          <parameter name="value" required="yes"/>
                          <parameter name="label"/>
                        </component-specification>
                        """));
                create("com/example/lib%d/Widget%d.html".formatted(l, c), """
                        <span jwcid="@If" condition="ognl:label != null"><span jwcid="@Insert" value="ognl:label"/>: </span>
                        <span jwcid="@Insert" value="ognl:value"/>
                        """);
            }
            create("com/example/lib%d/Lib%d.library".formatted(l, l),
                    DOCTYPE_4_0.formatted("library-specification") + "<library-specification>\n" + types + "</library-specification>\n");
        }
    }

    /** Komponenten in Ketten der Tiefe {@link #COMPONENT_DEPTH}: Comp n verwendet Comp n-1, sofern nicht Kettenanfang. */
    private void generateComponents() {
        for (int c = 0; c < COMPONENTS; c++) {
            create("WEB-INF/components/Comp%d.jwc".formatted(c), component("""
                    <component-specification class="com.example.components.Comp%d">
                      <description>Komponente %d</description>
                      <parameter name="value" required="yes"/>
                      <parameter name="label" aliases="caption"/>
                      <reserved-parameter name="href"/>
                      <property name="expanded" persist="session" initial-value="false"/>
                      <component id="text" type="Insert"><binding name="value" value="value"/></component>
                    </component-specification>
                    """.formatted(c, c)));
            final String inner = c % COMPONENT_DEPTH == 0 ? "" : """
                    <span jwcid="@Comp%d" value="ognl:value" label="ognl:label"><span jwcid="@RenderBody"/></span>
                    """.formatted(c - 1);
            create("WEB-INF/components/Comp%d.html".formatted(c), """
                    <div jwcid="@Any" class="ognl:expanded ? 'comp open' : 'comp'">
                    <span jwcid="@If" condition="ognl:label != null"><label><span jwcid="@Insert" value="ognl:label"/></label></span>
                    <span jwcid="text"/>
                    %s<span jwcid="@lib%d:Widget%d" value="ognl:value" label="ognl:label"/>
                    <a jwcid="@DirectLink" listener="listener:onToggle">umschalten</a>
                    <span jwcid="@RenderBody"/>
                    </div>
                    """.formatted(inner, c % LIBRARIES, c % LIBRARY_COMPONENTS));
            create("com/example/components/Comp%d.java".formatted(c), """
                    package com.example.components;
                    public abstract class Comp%d extends org.apache.tapestry.BaseComponent {
                        public abstract Object getValue();
                        public abstract String getLabel();
                        public abstract boolean isExpanded();
                        public abstract void setExpanded(boolean expanded);
                        public void onToggle() { setExpanded(!isExpanded()); }
                    }
                    """.formatted(c));
        }
    }

    /** Basisklasse und Stylesheet je Modul sowie eine spezifikationslose Seite im Kontext-Root. */
    private void generateModule(int m) {
        create("com/example/module%d/ModuleBasePage.java".formatted(m), """
                package com.example.module%d;
                public abstract class ModuleBasePage extends com.example.base.AppBasePage {
                    public String getModuleTitle() { return "Modul %d"; }
                    public abstract com.example.domain.Address getCurrentAddress();
                }
                """.formatted(m, m));
        create("css/module%d.css".formatted(m), "body { margin: 0; }\n");
        create("info/Info%d.html".formatted(m), """
                <html><body>
                <span jwcid="@Insert" value="literal:Information %d"/>
                <a jwcid="@PageLink" page="module%d/Page%d">Zum Modul</a>
                </body></html>
                """.formatted(m, m, m * PAGES_PER_MODULE));
    }

    private void generatePage(int p) {
        final int comp = p % COMPONENTS;
        final int lib = p % LIBRARIES;
        final int widget = p % LIBRARY_COMPONENTS;
        final int next = (p + 1) % PAGES;
        final int previous = (p + PAGES - 1) % PAGES;
        final int linked = (p + PAGES_PER_MODULE) % PAGES;
        create(pagePath(p, "page"), page("""
                <page-specification class="%s">
                  <description>Seite %d</description>
                  <meta key="org.apache.tapestry.page-title" value="Seite %d"/>
                  <property name="counter" initial-value="0"/>
                  <property name="selectedTab" persist="session" initial-value="literal:overview"/>
                  <inject property="nextPage" type="page" object="Page%d"/>
                  <inject property="visitState" type="state" object="visit"/>
                  <bean name="cache" class="HashMap"/>
                  <bean name="rowList" class="ArrayList" lifecycle="render"/>
                  <asset name="logo" path="logo.png"/>
                  <asset name="stylesheet" path="context:/css/%s.css"/>
                  <component id="greeting" type="Insert"><binding name="value" value="user.name"/></component>
                  <component id="save" type="DirectLink"><binding name="listener" value="listener:onSave"/></component>
                  <component id="custom" type="Comp%d">
                    <binding name="value" value="userName"/>
                    <binding name="label" value="message:title"/>
                  </component>
                  <component id="copy" copy-of="greeting"/>
                  <component id="rows" type="For">
                    <binding name="source" value="user.customer.orders[0].lines"/>
                    <binding name="value" value="line"/>
                    <binding name="index" value="rowIndex"/>
                    <binding name="element" value="literal:tr"/>
                  </component>
                  <component id="lineName" type="Insert"><binding name="value" value="line.product.name"/></component>
                </page-specification>
                """.formatted(pageClassName(p), p, p, next, module(p), comp)));

        final StringBuilder sections = new StringBuilder();
        for (int s = 0; s < SECTIONS; s++) {
            sections.append("""
                    <div jwcid="section%d@If" condition="ognl:user.customer.orders.size() > %d">
                      <h2><span jwcid="@Insert" value="message:section-%d"/></h2>
                      <ul><li jwcid="addresses%d@For" source="ognl:user.customer.addresses" value="ognl:currentAddress" element="li">
                        <span jwcid="@Insert" value="ognl:currentAddress.street + ', ' + currentAddress.city"/>
                        <span jwcid="@Insert" value="ognl:currentAddress.country.isoCode"/>
                      </li></ul>
                      <span jwcid="@Insert" value="ognl:user.customer.premium ? 'Premium' : 'Standard'"/>
                      <span jwcid="@Insert" value="ognl:@java.lang.Math@max(counter, %d)"/>
                      <span jwcid="@Insert" value="ognl:user.customer.orders[%d].status"/>
                      <span jwcid="@Comp%d" value="ognl:user.customer.orders[%d].total" label="message:section-%d"/>
                    </div>
                    """.formatted(s, s, s, s, s, s, (comp + s) % COMPONENTS, s, s));
            if (s == 0) sections.append("<p jwcid=\"@Else\">Keine Bestellungen</p>\n");
        }
        final String faults = !isFaulty(p) ? "" : """
                <span jwcid="@Insert" value="ognl:user.customer.unknownProperty"/>
                <span key="missing-key">Fehlt</span>
                <span jwcid="@Comp%d" caption="literal:veralteter Alias"/>
                """.formatted(comp);
        create(pagePath(p, "html"), """
                <html><head><link jwcid="@Any" rel="stylesheet" href="asset:stylesheet"/></head>
                <body jwcid="$content$">
                <h1><span key="title">Titel</span> – <span jwcid="header"/></h1>
                <p>Text der Seite %d</p>
                <span jwcid="greeting"/>
                <a jwcid="save">Speichern</a>
                <span jwcid="custom">Inhalt</span>
                <span jwcid="copy"/>
                <span jwcid="@Insert" value="ognl:user.address.city"/>
                <span jwcid="city@Insert" value="ognl:user.addresses[0].street"/>
                <span jwcid="@Insert" value="message:title"/>
                <a jwcid="@DirectLink" listener="listener:onSave" parameters="ognl:{counter, userName}">Link</a>
                <span jwcid="@Comp%d" value="ognl:userName" label="literal:Beschriftung"/>
                <span jwcid="@lib%d:Widget%d" value="ognl:counter + 1" label="message:global"/>
                <img src="asset:logo"/>
                <span jwcid="@If" condition="ognl:loggedIn"><span jwcid="@Insert" value="ognl:sessionData.loggedInUser.customer.name"/></span>
                <table>
                  <tr jwcid="rows">
                    <td><span jwcid="lineName"/></td>
                    <td><span jwcid="@Insert" value="ognl:line.quantity * line.product.price"/></td>
                    <td><span jwcid="@If" condition="ognl:line.product.category.parent != null"><span jwcid="@Insert" value="ognl:line.product.category.parent.name"/></span></td>
                    <td><a jwcid="@DirectLink" listener="listener:onDelete" parameters="ognl:line.id">Löschen</a></td>
                  </tr>
                </table>
                <form jwcid="search@Form" listener="listener:onSearch">
                  <input jwcid="query@TextField" value="ognl:sessionData.lastSearch"/>
                  <input jwcid="@Submit" label="message:search"/>
                </form>
                %s<span jwcid="@Insert" value="ognl:moduleTitle"/>
                <a jwcid="@PageLink" page="%s/Page%d">Weiter</a>
                %s</body></html>
                """.formatted(p, comp, lib, widget, sections, module(linked), linked, faults));

        final StringBuilder messages = new StringBuilder("title=Seite %d\nsubtitle=Untertitel %d\npage-title=Seite %d\n".formatted(p, p, p));
        for (int s = 0; s < SECTIONS; s++) messages.append("section-%d=Abschnitt %d\n".formatted(s, s));
        create(pagePath(p, "properties"), messages.toString());
        create(pagePath(p, "properties").replace(".properties", "_de.properties"), "title=Seite %d\n".formatted(p));
        create(pagePath(p, "properties").replace(".properties", "_en.properties"), "title=Page %d\n".formatted(p));

        create("com/example/%s/Page%d.java".formatted(module(p), p), """
                package com.example.%s;
                import %s*;
                import com.example.domain.OrderLine;
                public abstract class Page%d extends ModuleBasePage {
                    // Notiz
                    public abstract String getUserName();
                    public abstract int getCounter();
                    public abstract OrderLine getLine();
                    @InjectPage("Page%d") public abstract Object getPreviousPage();
                    @InjectComponent("greeting") public abstract Object getGreetingComponent();
                    @Message public abstract String getPageTitle();
                    @Component(type = "Insert", bindings = {"value=pageTitle"}) public abstract Object getHeader();
                    @InitialValue("ognl:0") public abstract int getRowIndex();
                    public void onSave() {}
                    public void onDelete(Long id) {}
                }
                """.formatted(module(p), ANNOTATIONS, p, previous));
    }
}
