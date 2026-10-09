package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.List;

/**
 * Application State Objects (User's Guide, "Managing server-side state"): Namen aus hivemodule.xml –
 * Tapestry definiert "visit"/"global" in tapestry.state.FactoryObjects, die Anwendung eigene in
 * tapestry.state.ApplicationObjects (die gleichnamige überschreiben).
 */
public class StateObjectsTest extends TapestryTestCase {

    /** Wie im Tapestry-JAR: hivemodule.xml bindet tapestry.state.xml als Sub-Modul ein. */
    private void addFrameworkModules() {
        myFixture.addFileToProject("META-INF/hivemodule.xml", """
                <module id="tapestry" version="4.0.0"><sub-module descriptor="tapestry.state.xml"/></module>
                """);
        myFixture.addFileToProject("META-INF/tapestry.state.xml", """
                <module id="tapestry.state" version="4.0.0">
                  <contribution configuration-id="FactoryObjects">
                    <state-object name="global" scope="application"/>
                    <state-object name="visit" scope="session"/>
                  </contribution>
                </module>
                """);
    }

    private void addApplicationModule() {
        myFixture.addFileToProject("web/WEB-INF/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="tapestry.state.ApplicationObjects">
                    <state-object name="registration-data" scope="session"/>
                    <state-object name="visit" scope="session"/>
                  </contribution>
                </module>
                """);
    }

    private XmlAttributeValue resolveStateObject() {
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        return (XmlAttributeValue) target;
    }

    public void testStateBindingResolvesToFactoryObject() {
        addFrameworkModules();
        configure("Home.html", "<html><body><span jwcid=\"@If\" condition=\"state:glo<caret>bal\"/></body></html>");
        final XmlAttributeValue target = resolveStateObject();
        assertEquals("global", target.getValue());
        assertEquals("tapestry.state.xml", target.getContainingFile().getName());
    }

    public void testApplicationObjectOverridesFactoryObject() {
        addFrameworkModules();
        addApplicationModule();
        configure("Home.html", "<html><body><span jwcid=\"@If\" condition=\"state:vi<caret>sit\"/></body></html>");
        assertEquals("hivemodule.xml", resolveStateObject().getContainingFile().getName());
        assertEquals("WEB-INF", resolveStateObject().getContainingFile().getVirtualFile().getParent().getName());
    }

    public void testInjectTypeStateInSpecification() {
        addFrameworkModules();
        addApplicationModule();
        configure("Reg.page", page("""
                <page-specification>
                  <inject property="registration" type="state" object="registration-<caret>data"/>
                </page-specification>
                """));
        assertEquals("registration-data", resolveStateObject().getValue());
    }

    public void testInjectStateFlagAnnotation() {
        addFrameworkModules();
        configure("com/example/Flags.java", """
                package com.example;
                public abstract class Flags {
                    @org.apache.tapestry.annotations.InjectStateFlag("vi<caret>sit") public abstract boolean getVisitExists();
                }
                """);
        assertEquals("visit", resolveStateObject().getValue());
    }

    public void testCompletionAndUnknownStateObject() {
        addFrameworkModules();
        addApplicationModule();
        configure("Home.html", "<html><body><span jwcid=\"@If\" condition=\"state:<caret>\"/></body></html>");
        final List<String> items = completeAtCaret();
        assertTrue(items.toString(), items.containsAll(List.of("visit", "global", "registration-data")));

        configure("Other.html", "<html><body><span jwcid=\"@If\" condition=\"state:doesNotExist\"/></body></html>");
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertTrue(warnings.toString(), warnings.contains("Unknown application state object 'doesNotExist'"));
    }

    public void testNoWarningWithoutModuleDescriptors() {
        configure("Home.html", "<html><body><span jwcid=\"@If\" condition=\"state:visit\"/></body></html>");
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("state object")));
    }
}
