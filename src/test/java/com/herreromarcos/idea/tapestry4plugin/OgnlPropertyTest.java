package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.List;

/** OGNL-Eigenschaften, die Tapestry ohne Java-Getter bereitstellt (Parameter, Injects …) bzw. nur per Setter. */
public class OgnlPropertyTest extends TapestryTestCase {
    private static final String WIDGET_SPEC = component("""
            <component-specification>
              <parameter name="disabled"/>
              <parameter name="value" property="currentValue"/>
              <inject property="userService" object="service:app.UserService"/>
              <bean name="helper" class="java.lang.Object" property="helperBean"/>
              <asset name="icon" path="icon.png" property="iconAsset"/>
              <component id="field" type="Insert" property="fieldComponent">
                <binding name="value" value="disabled"/>
              </component>
            </component-specification>
            """);

    public void testParameterOfOwnComponentIsAnOgnlProperty() {
        final List<String> warnings = warnings("Widget.jwc", WIDGET_SPEC);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("'disabled'")));
    }

    public void testSpecDerivedPropertiesInTemplate() {
        myFixture.addFileToProject("Widget.jwc", WIDGET_SPEC);
        final List<String> warnings = warnings("Widget.html", """
                <div jwcid="$content$">
                  <span jwcid="@Insert" value="ognl:disabled"/>
                  <span jwcid="@Insert" value="ognl:currentValue"/>
                  <span jwcid="@Insert" value="ognl:userService"/>
                  <span jwcid="@Insert" value="ognl:helperBean"/>
                  <span jwcid="@Insert" value="ognl:iconAsset"/>
                  <span jwcid="@Insert" value="ognl:fieldComponent"/>
                </div>
                """);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.startsWith("Property")));
    }

    public void testParameterPropertyResolvesToDeclaration() {
        myFixture.addFileToProject("Widget.jwc", WIDGET_SPEC);
        configure("Widget.html", "<div jwcid=\"$content$\"><span jwcid=\"@Insert\" value=\"ognl:curr<caret>entValue\"/></div>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("currentValue", ((XmlAttributeValue) target).getValue());
    }

    public void testSetterOnlyPropertyIsResolved() {
        myFixture.addFileToProject("com/example/Editor.java", """
                package com.example;
                public abstract class Editor {
                    public void setSelection(String selection) {}
                }
                """);
        myFixture.addFileToProject("Editor.page", page("<page-specification class=\"com.example.Editor\"/>"));
        configure("Editor.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:sel<caret>ection\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("setSelection", ((PsiMethod) target).getName());
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("'selection'")));
    }

    public void testUnknownPropertyIsStillReported() {
        myFixture.addFileToProject("Widget.jwc", WIDGET_SPEC);
        final List<String> warnings = warnings("Widget.html",
                "<div jwcid=\"$content$\"><span jwcid=\"@Insert\" value=\"ognl:doesNotExist\"/></div>");
        assertTrue(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("'doesNotExist'")));
    }
}
