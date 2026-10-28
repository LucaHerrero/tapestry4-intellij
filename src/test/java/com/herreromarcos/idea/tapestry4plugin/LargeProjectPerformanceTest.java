package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiMethod;

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
 * (optional {@code -Pperformance.pages=6000}). Die Messwerte stehen in {@code build/reports/performance.txt}.
 */
public class LargeProjectPerformanceTest extends TapestryTestCase {
    private static final int PAGES = Integer.getInteger("tapestry.performance.pages", 3000);
    private static final int PAGES_PER_MODULE = 100;
    private static final int COMPONENTS = Math.max(10, PAGES / 10);
    private static final int LIBRARIES = 5;
    private static final int LIBRARY_COMPONENTS = 20;
    private static final int TYPING_ITERATIONS = 100;
    private static final int SPEC_EDIT_ITERATIONS = 30;
    private static final int FILE_CREATE_ITERATIONS = 30;

    private final List<String> report = new ArrayList<>();

    @Override
    protected boolean shouldRunTest() {
        return Boolean.getBoolean("tapestry.performance") && super.shouldRunTest();
    }

    public void testLargeProject() throws IOException {
        final long generation = millis(this::generateProject);
        line("Projekt: %d Seiten, %d Komponenten, %d Bibliotheken – erzeugt in %d ms", PAGES, COMPONENTS, LIBRARIES, generation);

        final int page = PAGES / 2;
        final VirtualFile template = file(pagePath(page, "html"));
        final VirtualFile spec = file(pagePath(page, "page"));

        myFixture.configureFromExistingVirtualFile(template);
        line("Template, erste Hervorhebung (kalte Caches): %d ms", millis(() -> myFixture.doHighlighting()));
        measure("Template, erneute Hervorhebung", 20, () -> myFixture.doHighlighting());

        myFixture.configureFromExistingVirtualFile(spec);
        line("Spezifikation, erste Hervorhebung: %d ms", millis(() -> myFixture.doHighlighting()));
        measure("Spezifikation, erneute Hervorhebung", 20, () -> myFixture.doHighlighting());

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
        line("Seiten neu berechnen: %d ms (%d Seiten)", millis(() -> TapestryRegistry.getPages(getProject())),
                TapestryRegistry.getPages(getProject()).size());
        final PsiClass pageClass = myFixture.findClass(pageClassName(page));
        line("Spezifikationen zu einer Klasse (kalt): %d ms", millis(() -> TapestryModel.findSpecsForClass(pageClass)));

        // Completion
        myFixture.configureFromExistingVirtualFile(template);
        measure("Completion jwcid=\"@\"", 10, () -> complete("jwcid=\"@Insert\"", "jwcid=\"@".length()));
        measure("Completion ognl:user.", 10, () -> complete("ognl:user.address.city", "ognl:user.".length()));
        measure("Completion message:", 10, () -> complete("message:title", "message:".length()));

        // Java: Gutter-Icons und Annotationen der Seitenklasse
        myFixture.configureFromExistingVirtualFile(pageClass.getContainingFile().getVirtualFile());
        measure("Seitenklasse hervorheben (Gutter, Annotationen)", 10, () -> myFixture.doHighlighting());

        // Find Usages eines Getters, der in allen Templates per OGNL verwendet wird
        final PsiMethod getter = myFixture.findClass("com.example.domain.User").findMethodsByName("getAddress", false)[0];
        final int[] usages = {0};
        line("Find Usages getAddress (alle Templates): %d ms, %d Treffer",
                millis(() -> usages[0] = myFixture.findUsages(getter).size()), usages[0]);

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

    private void create(String path, String text) {
        try {
            myFixture.getTempDirFixture().createFile(path, text);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void generateProject() {
        create("com/example/domain/Address.java", """
                package com.example.domain;
                public class Address { public String getCity() { return null; } public String getStreet() { return null; } }
                """);
        create("com/example/domain/User.java", """
                package com.example.domain;
                public class User {
                    public Address getAddress() { return null; }
                    public String getName() { return null; }
                    public java.util.List<Address> getAddresses() { return null; }
                }
                """);
        final StringBuilder libraries = new StringBuilder();
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
                create("com/example/lib%d/Widget%d.html".formatted(l, c), "<span jwcid=\"@Insert\" value=\"ognl:value\"/>");
            }
            create("com/example/lib%d/Lib%d.library".formatted(l, l),
                    DOCTYPE_4_0.formatted("library-specification") + "<library-specification>\n" + types + "</library-specification>\n");
            libraries.append("  <library id=\"lib%d\" specification-path=\"/com/example/lib%d/Lib%d.library\"/>\n".formatted(l, l, l));
        }
        create("WEB-INF/app.application", DOCTYPE_4_0.formatted("application") + """
                <application name="app">
                  <meta key="org.apache.tapestry.bean-class-packages" value="java.util"/>
                %s</application>
                """.formatted(libraries));
        create("WEB-INF/app.properties", "global=Global\n");

        for (int c = 0; c < COMPONENTS; c++) {
            create("WEB-INF/components/Comp%d.jwc".formatted(c), component("""
                    <component-specification class="com.example.components.Comp%d">
                      <description>Komponente %d</description>
                      <parameter name="value" required="yes"/>
                      <parameter name="label" aliases="caption"/>
                      <reserved-parameter name="href"/>
                      <component id="text" type="Insert"><binding name="value" value="value"/></component>
                    </component-specification>
                    """.formatted(c, c)));
            create("WEB-INF/components/Comp%d.html".formatted(c), "<span jwcid=\"text\"/><span jwcid=\"@RenderBody\"/>");
            create("com/example/components/Comp%d.java".formatted(c), """
                    package com.example.components;
                    public abstract class Comp%d extends org.apache.tapestry.BaseComponent {
                        public abstract Object getValue();
                        public abstract String getLabel();
                    }
                    """.formatted(c));
        }

        for (int p = 0; p < PAGES; p++) {
            final int comp = p % COMPONENTS;
            final int lib = p % LIBRARIES;
            final int widget = p % LIBRARY_COMPONENTS;
            final int other = (p + 1) % PAGES;
            create(pagePath(p, "page"), page("""
                    <page-specification class="%s">
                      <description>Seite %d</description>
                      <property name="counter" initial-value="0"/>
                      <inject property="nextPage" type="page" object="Page%d"/>
                      <bean name="cache" class="HashMap"/>
                      <asset name="logo" path="logo.png"/>
                      <component id="greeting" type="Insert"><binding name="value" value="user.name"/></component>
                      <component id="save" type="DirectLink"><binding name="listener" value="listener:onSave"/></component>
                      <component id="custom" type="Comp%d">
                        <binding name="value" value="userName"/>
                        <binding name="label" value="message:title"/>
                      </component>
                      <component id="copy" copy-of="greeting"/>
                    </page-specification>
                    """.formatted(pageClassName(p), p, other, comp)));
            create(pagePath(p, "html"), """
                    <html><body jwcid="$content$">
                    <h1><span key="title">Titel</span></h1>
                    <p>Text der Seite %d</p>
                    <span jwcid="greeting"/>
                    <a jwcid="save">Speichern</a>
                    <span jwcid="custom">Inhalt</span>
                    <span jwcid="copy"/>
                    <span jwcid="@Insert" value="ognl:user.address.city"/>
                    <span jwcid="city@Insert" value="ognl:user.addresses[0].street"/>
                    <span jwcid="@Insert" value="message:title"/>
                    <a jwcid="@DirectLink" listener="listener:onSave" parameters="ognl:{counter, userName}">Link</a>
                    <span jwcid="@Comp%d" value="ognl:userName" caption="literal:Beschriftung"/>
                    <span jwcid="@lib%d:Widget%d" value="ognl:counter + 1" label="message:global"/>
                    <img src="asset:logo"/>
                    </body></html>
                    """.formatted(p, comp, lib, widget));
            create(pagePath(p, "properties"), "title=Seite %d\nsubtitle=Untertitel %d\n".formatted(p, p));
            create("com/example/%s/Page%d.java".formatted(module(p), p), """
                    package com.example.%s;
                    public abstract class Page%d extends org.apache.tapestry.html.BasePage {
                        public abstract String getUserName();
                        public abstract com.example.domain.User getUser();
                        public abstract int getCounter();
                        public void onSave() {}
                    }
                    """.formatted(module(p), p));
        }
        create("WEB-INF/logo.png", "");
    }
}
