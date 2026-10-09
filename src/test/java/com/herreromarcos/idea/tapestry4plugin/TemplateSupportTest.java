package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.references.BindingPrefixReference;
import com.herreromarcos.idea.tapestry4plugin.references.TapestryReference;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.Arrays;
import java.util.List;

/** jwcid, Bindings, Completion und Prüfungen in HTML-Templates. */
public class TemplateSupportTest extends TapestryTestCase {

    private void configureTemplate(final String body) {
        configure("Home.html", "<html><body>" + body + "</body></html>");
    }

    public void testDeclaredComponentIdResolvesToSpecification() {
        configureTemplate("<span jwcid=\"gree<caret>ting\"/>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("greeting", ((XmlAttributeValue) target).getValue());
        assertEquals("Home.page", target.getContainingFile().getName());
    }

    public void testImplicitComponentTypeResolvesViaFrameworkLibrary() {
        configureTemplate("<span jwcid=\"@Ins<caret>ert\" value=\"ognl:userName\"/>");
        assertEquals("Insert.jwc", ((PsiFile) resolveAtCaret()).getName());
    }

    public void testNamedImplicitComponentType() {
        configureTemplate("<a jwcid=\"save@Direct<caret>Link\" listener=\"listener:onSave\">x</a>");
        assertEquals("DirectLink.jwc", ((PsiFile) resolveAtCaret()).getName());
    }

    public void testOgnlPropertyChain() {
        configureTemplate("<span jwcid=\"@Insert\" value=\"ognl:user.addr<caret>ess.city\"/>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("getAddress", ((PsiMethod) target).getName());
    }

    public void testOgnlSpecProperty() {
        configureTemplate("<span jwcid=\"@Insert\" value=\"ognl:coun<caret>ter\"/>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("counter", ((XmlAttributeValue) target).getValue());
    }

    public void testListenerResolvesToMethod() {
        configureTemplate("<a jwcid=\"@DirectLink\" listener=\"listener:on<caret>Save\">x</a>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("onSave", ((PsiMethod) target).getName());
    }

    public void testMessageKeyResolves() {
        configureTemplate("<span jwcid=\"@Insert\" value=\"message:ti<caret>tle\"/>");
        assertNotNull(resolveAtCaret());
    }

    public void testAssetResolves() {
        configureTemplate("<img jwcid=\"@Any\" src=\"asset:lo<caret>go\"/>");
        // HTML legt auf src zusätzlich eine eigene URL-Referenz – gezielt die Asset-Referenz prüfen
        final XmlAttributeValue value = PsiTreeUtil.getParentOfType(
                myFixture.getFile().findElementAt(myFixture.getCaretOffset()), XmlAttributeValue.class);
        assertNotNull(value);
        final PsiElement target = Arrays.stream(value.getReferences())
                .filter(reference -> reference instanceof TapestryReference && !(reference instanceof BindingPrefixReference))
                .findFirst().map(PsiReference::resolve).orElse(null);
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("logo", ((XmlAttributeValue) target).getValue());
    }

    public void testJwcidCompletion() {
        configureTemplate("<span jwcid=\"<caret>\"/>");
        final List<String> items = completeAtCaret();
        assertTrue(items.toString(), items.containsAll(List.of("greeting", "@Insert", "@DirectLink")));
    }

    public void testOgnlCompletion() {
        configureTemplate("<span jwcid=\"@Insert\" value=\"ognl:user.<caret>\"/>");
        final List<String> items = completeAtCaret();
        assertTrue(items.toString(), items.containsAll(List.of("address", "name")));
    }

    public void testParameterAttributeCompletion() {
        configureTemplate("<span jwcid=\"@Insert\" <caret>/>");
        final List<String> items = completeAtCaret();
        assertTrue(items.toString(), items.containsAll(List.of("value", "raw")));
    }

    public void testHighlighting() {
        myFixture.enableInspections(new HtmlUnknownAttributeInspection());
        configure("Home.html", """
                <html><body>
                <span jwcid="greeting"/>
                <span jwcid="missing"/>
                <span jwcid="@Insert"/>
                <span jwcid="@Insert" value="ognl:user.nope"/>
                <a jwcid="@DirectLink" listener="listener:doesNotExist">x</a>
                <span jwcid="dup@Insert" value="literal:a"/>
                <span jwcid="dup@Insert" value="literal:b"/>
                </body></html>
                """);
        final List<String> messages = highlightingMessages(HighlightSeverity.WARNING);
        final String all = messages.toString();
        assertTrue(all, messages.contains("Component 'missing' is not declared in Home.page"));
        assertTrue(all, messages.contains("Required parameter 'value' of component 'Insert' is not bound"));
        assertTrue(all, messages.contains("Property 'nope' not found in User"));
        assertTrue(all, messages.contains("Listener method 'doesNotExist' not found in Home"));
        assertTrue(all, messages.contains("Duplicate component id 'dup' in template"));
        assertFalse(all, messages.stream().anyMatch(m -> m.contains("greeting")));
        assertFalse(all, messages.stream().anyMatch(m -> m.contains("Unknown html attribute")));
    }
}
