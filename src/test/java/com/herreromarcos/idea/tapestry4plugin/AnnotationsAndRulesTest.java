package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.List;

/** Referenzen in Annotationen, Skripten und validators: sowie die Regeln aus dem zweiten Doku-Abgleich. */
public class AnnotationsAndRulesTest extends TapestryTestCase {
    private static final String A = "org.apache.tapestry.annotations.";

    /** Ersetzt die Klasse Home aus dem Basis-Setup um ein zusätzliches Element; {@code <caret>} markiert die Position. */
    private void configureHomeClass(String member) {
        final String text = """
                package com.example;
                public abstract class Home {
                    public abstract String getUserName();
                    public abstract User getUser();
                    public void onSave() {}
                    %s
                }
                """.formatted(member);
        final int caret = text.indexOf(CARET);
        final com.intellij.openapi.vfs.VirtualFile file = myFixture.findFileInTempDir("com/example/Home.java");
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            try {
                com.intellij.openapi.vfs.VfsUtil.saveText(file, text.replace(CARET, ""));
            } catch (final java.io.IOException e) {
                throw new RuntimeException(e);
            }
        });
        myFixture.configureFromExistingVirtualFile(file);
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
    }

    // ---- Annotationen (Annotations-Referenz)

    public void testInjectPage() {
        configureHomeClass("@" + A + "InjectPage(\"Ho<caret>me\") public abstract Object getHomePage();");
        assertEquals("Home.page", ((PsiFile) resolveAtCaret()).getName());
    }

    public void testInjectComponent() {
        configureHomeClass("@" + A + "InjectComponent(\"gree<caret>ting\") public abstract Object getGreeting();");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("Home.page", target.getContainingFile().getName());
    }

    public void testMessageAnnotation() {
        configureHomeClass("@" + A + "Message(\"ti<caret>tle\") public abstract String getTitle();");
        assertInstanceOf(resolveAtCaret(), Property.class);
    }

    public void testComponentAnnotationTypeAndBinding() {
        configureHomeClass("@" + A + "Component(type = \"Ins<caret>ert\", bindings = {\"value=ognl:userName\"}) public abstract Object getText();");
        assertEquals("Insert.jwc", ((PsiFile) resolveAtCaret()).getName());
        final int binding = myFixture.getFile().getText().indexOf("userName\"");
        myFixture.getEditor().getCaretModel().moveToOffset(binding + 3);
        assertEquals("getUserName", ((PsiMethod) resolveAtCaret()).getName());
    }

    public void testInjectScriptRelativeToSpecification() {
        myFixture.addFileToProject("Home.script", "<script/>");
        configureHomeClass("@" + A + "InjectScript(\"Home.scr<caret>ipt\") public abstract Object getScript();");
        assertEquals("Home.script", ((PsiFile) resolveAtCaret()).getName());
    }

    public void testUnknownInjectPageIsReported() {
        configureHomeClass("@" + A + "InjectPage(\"DoesNotExist\") public abstract Object getMissing();");
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertTrue(warnings.toString(), warnings.contains("Unknown page 'DoesNotExist'"));
    }

    // ---- Script-Spezifikation: include-script

    public void testIncludeScriptResourcePath() {
        myFixture.addFileToProject("org/example/scripts/lib.js", "// js");
        configure("Focus.script", """
                <script><include-script resource-path="/org/example/scripts/li<caret>b.js"/></script>
                """);
        assertEquals("lib.js", ((PsiFile) resolveAtCaret()).getName());
    }

    // ---- validators: (User's Guide, "Input Validation")

    public void testValidatorsBeanAndMessageKey() {
        myFixture.addFileToProject("Val.properties", "email-format=Bad email\n");
        configure("Val.page", page("""
                <page-specification class="com.example.Home">
                  <bean name="myValidator" class="java.lang.Object"/>
                  <component id="email" type="Insert">
                    <binding name="value" value="validators:required,email[%email-format],$myVal<caret>idator"/>
                  </component>
                </page-specification>
                """));
        final PsiElement bean = resolveAtCaret();
        assertInstanceOf(bean, XmlAttributeValue.class);
        assertEquals("myValidator", ((XmlAttributeValue) bean).getValue());
        myFixture.getEditor().getCaretModel().moveToOffset(myFixture.getFile().getText().indexOf("email-format]") + 3);
        assertInstanceOf(resolveAtCaret(), Property.class);
    }

    // ---- Bibliotheken: Komponenten im Ordner der Library-Spezifikation

    public void testLibraryComponentInSameFolder() {
        myFixture.addFileToProject("App.application", DOCTYPE_4_0.formatted("application") + """
                <application><library id="lib" specification-path="/mylib/My.library"/></application>
                """);
        myFixture.addFileToProject("mylib/My.library", DOCTYPE_4_0.formatted("library-specification") + "<library-specification/>");
        myFixture.addFileToProject("mylib/Gadget.jwc", component("<component-specification/>"));
        configure("Home.html", "<html><body><span jwcid=\"@lib:Gad<caret>get\"/></body></html>");
        assertEquals("Gadget.jwc", ((PsiFile) resolveAtCaret()).getName());
    }

    // ---- deprecated, Aliase, reservierte Namen, Konflikte, informelle Parameter, ids

    public void testDeprecatedComponentParameterAndAlias() {
        myFixture.addFileToProject("Legacy.jwc", component("""
                <component-specification deprecated="yes">
                  <parameter name="value" aliases="selected"/>
                  <parameter name="label" deprecated="true"/>
                </component-specification>
                """));
        final List<String> warnings = warnings("Home.html",
                "<html><body><span jwcid=\"@Legacy\" selected=\"ognl:userName\" label=\"x\"/></body></html>");
        assertTrue(warnings.toString(), warnings.contains("Component 'Legacy' is deprecated"));
        assertTrue(warnings.toString(), warnings.contains("Parameter alias 'selected' of component 'Legacy' is deprecated, use 'value'"));
        assertTrue(warnings.toString(), warnings.contains("Parameter 'label' of component 'Legacy' is deprecated"));
    }

    public void testReservedParameter() {
        myFixture.addFileToProject("Link.jwc", component("""
                <component-specification><reserved-parameter name="href"/></component-specification>
                """));
        final List<String> warnings = warnings("Home.html", """
                <html><body><a jwcid="@Link" href="#">wysiwyg</a><a jwcid="@Link" href="ognl:userName">x</a></body></html>
                """);
        assertEquals(warnings.toString(), 1, warnings.stream().filter(m -> m.contains("reserved by component 'Link'")).count());
    }

    public void testBindingConflictBetweenSpecificationAndTemplate() {
        final List<String> errors = warnings("Home.html", """
                <html><body><span jwcid="greeting" value="ognl:userName"/><span jwcid="greeting" value="preview"/></body></html>
                """);
        assertEquals(errors.toString(), 1, errors.stream().filter(m -> m.equals("Parameter 'value' is already bound in the specification")).count());
    }

    public void testInformalParametersForbidden() {
        myFixture.addFileToProject("Strict2.jwc", component("""
                <component-specification allow-informal-parameters="no"/>
                """));
        final List<String> errors = warnings("Home.html", """
                <html><body><span jwcid="@Strict2" class="literal-is-fine" title="ognl:userName"/></body></html>
                """);
        assertEquals(errors.toString(), 1, errors.stream().filter(m -> m.equals("Component 'Strict2' does not allow informal parameters")).count());
    }

    public void testInvalidComponentIds() {
        assertTrue(warnings("Home.html", "<html><body><span jwcid=\"my-id@Insert\" value=\"ognl:userName\"/></body></html>")
                .contains("Invalid component id 'my-id': must be a Java identifier"));
        assertTrue(warnings("Bad.page", page("<page-specification><component id=\"9lives\" type=\"Insert\"/></page-specification>"))
                .contains("Invalid component id '9lives': must be a Java identifier"));
    }
}
