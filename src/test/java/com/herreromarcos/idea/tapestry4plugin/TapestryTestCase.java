package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.xml.XmlElementDescriptor;

import java.util.Arrays;
import java.util.List;

/**
 * Gemeinsames Testprojekt: Mini-Framework.library (Insert, DirectLink), Seite Home (Template, .page,
 * Klasse, Properties) und Hilfsmethoden.
 */
public abstract class TapestryTestCase extends LightJavaCodeInsightFixtureTestCase {
    protected static final String DOCTYPE_4_0 = """
            <?xml version="1.0"?>
            <!DOCTYPE %s PUBLIC "-//Apache Software Foundation//Tapestry Specification 4.0//EN"
              "http://jakarta.apache.org/tapestry/dtd/Tapestry_4_0.dtd">
            """;
    protected static final String DOCTYPE_4_1 = """
            <?xml version="1.0"?>
            <!DOCTYPE %s PUBLIC "-//Apache Software Foundation//Tapestry Specification 4.1//EN"
              "http://tapestry.apache.org/dtd/Tapestry_4_1.dtd">
            """;
    protected static final String CARET = "<caret>";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addFileToProject("org/apache/tapestry/Framework.library", """
                <library-specification>
                  <component-type type="Insert" specification-path="components/Insert.jwc"/>
                  <component-type type="DirectLink" specification-path="link/DirectLink.jwc"/>
                </library-specification>
                """);
        myFixture.addFileToProject("org/apache/tapestry/components/Insert.jwc", """
                <component-specification allow-body="no">
                  <parameter name="value" required="yes"/>
                  <parameter name="raw"/>
                </component-specification>
                """);
        myFixture.addFileToProject("org/apache/tapestry/link/DirectLink.jwc", """
                <component-specification>
                  <parameter name="listener" required="yes"/>
                  <parameter name="parameters"/>
                </component-specification>
                """);
        // Basisklassen wie im echten Tapestry-JAR: ohne sie hätte eine Seite/Komponente ohne class-Attribut
        // keine Klasse, gegen die OGNL geprüft werden kann
        myFixture.addFileToProject("org/apache/tapestry/BaseComponent.java", """
                package org.apache.tapestry;
                public abstract class BaseComponent {
                    public java.util.Map getComponents() { return null; }
                    public java.util.Map getAssets() { return null; }
                    public Object getMessages() { return null; }
                }
                """);
        myFixture.addFileToProject("org/apache/tapestry/html/BasePage.java", """
                package org.apache.tapestry.html;
                public abstract class BasePage extends org.apache.tapestry.BaseComponent {
                    public Object getVisit() { return null; }
                }
                """);
        myFixture.addFileToProject("com/example/Address.java", """
                package com.example;
                public class Address { public String getCity() { return null; } }
                """);
        myFixture.addFileToProject("com/example/User.java", """
                package com.example;
                public class User { public Address getAddress() { return null; } public String getName() { return null; } }
                """);
        myFixture.addFileToProject("com/example/Home.java", """
                package com.example;
                public abstract class Home {
                    public abstract String getUserName();
                    public abstract User getUser();
                    public void onSave() {}
                }
                """);
        myFixture.addFileToProject("Home.page", page("""
                <page-specification class="com.example.Home">
                  <property name="counter"/>
                  <component id="greeting" type="Insert">
                    <binding name="value" value="userName"/>
                  </component>
                  <asset name="logo" path="logo.png"/>
                </page-specification>
                """));
        myFixture.addFileToProject("Home.properties", "title=Welcome\n");
    }

    /** Seitenspezifikation (Tapestry 4.0) mit dem angegebenen Inhalt. */
    protected static String page(final String body) {
        return DOCTYPE_4_0.formatted("page-specification") + body;
    }

    /** Komponentenspezifikation (Tapestry 4.0) mit dem angegebenen Inhalt. */
    protected static String component(final String body) {
        return DOCTYPE_4_0.formatted("component-specification") + body;
    }

    /** Legt die Datei an und öffnet sie im Editor; {@code <caret>} markiert die Cursorposition. */
    protected PsiFile configure(final String path, final String textWithCaret) {
        final int caret = textWithCaret.indexOf(CARET);
        final PsiFile file = myFixture.addFileToProject(path, textWithCaret.replace(CARET, ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
        return file;
    }

    protected PsiElement resolveAtCaret() {
        final PsiReference reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", reference);
        return reference.resolve();
    }

    protected List<String> completeAtCaret() {
        myFixture.completeBasic();
        final List<String> items = myFixture.getLookupElementStrings();
        assertNotNull("completion was inserted directly or nothing was offered", items);
        return items;
    }

    protected List<String> highlightingMessages(final HighlightSeverity minimum) {
        return myFixture.doHighlighting().stream()
                .filter(info -> info.getSeverity().compareTo(minimum) >= 0)
                .map(info -> info.getDescription())
                .toList();
    }

    /** Legt die Datei an und liefert alle Meldungen ab Warnung. */
    protected List<String> warnings(final String path, final String text) {
        configure(path, text);
        return highlightingMessages(HighlightSeverity.WARNING);
    }

    protected boolean hasErrorOn(final String text) {
        return myFixture.doHighlighting().stream()
                .anyMatch(info -> info.getSeverity() == HighlightSeverity.ERROR && info.getText().contains(text));
    }

    /** Laut DTD erlaubte Kindelemente des Tags. */
    protected static List<String> allowedChildren(final XmlTag tag) {
        final XmlElementDescriptor descriptor = tag.getDescriptor();
        assertNotNull("no DTD descriptor for <" + tag.getName() + ">", descriptor);
        return Arrays.stream(descriptor.getElementsDescriptors(tag)).map(XmlElementDescriptor::getName).toList();
    }

    protected static XmlTag rootTag(final PsiFile file) {
        assertInstanceOf(file, XmlFile.class);
        return ((XmlFile) file).getRootTag();
    }
}
