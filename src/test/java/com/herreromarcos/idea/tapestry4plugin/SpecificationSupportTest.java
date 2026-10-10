package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.List;

/** Referenzen in Spezifikationen, Original-DTDs und Zuordnung Klasse → Spezifikation. */
public class SpecificationSupportTest extends TapestryTestCase {

    public void testBindingNameResolvesToParameter() {
        configure("Other.page", page("""
                <page-specification class="com.example.Home">
                  <component id="x" type="Insert"><binding name="val<caret>ue" value="userName"/></component>
                </page-specification>
                """));
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("Insert.jwc", target.getContainingFile().getName());
    }

    public void testDtd40TagCompletion() {
        configure("Third.page", page("""
                <page-specification>
                  <<caret>
                </page-specification>
                """));
        final List<String> items = completeAtCaret();
        assertTrue(items.toString(), items.containsAll(List.of("component", "inject")));
    }

    public void testOriginalDtd41ContentModelAndValidation() {
        final PsiFile file = myFixture.addFileToProject("Comp41.jwc", DOCTYPE_4_1.formatted("component-specification") + """
                <component-specification>
                  <component id="a" type="Insert"/>
                </component-specification>
                """);
        final XmlTag component = rootTag(file).findFirstSubTag("component");
        // Original 4.1-DTD: <!ELEMENT component (meta | binding | inherited-binding )*>
        assertNotNull(component);
        assertSameElements(allowedChildren(component), "meta", "binding", "inherited-binding");

        configure("Invalid41.jwc", DOCTYPE_4_1.formatted("component-specification") + """
                <component-specification>
                  <unknown-element/>
                </component-specification>
                """);
        assertTrue(hasErrorOn());
    }

    public void testOriginalScriptDtds() {
        final String[][] doctypes = {
                {"-//Apache Software Foundation//Tapestry Script Specification 3.0//EN", "http://jakarta.apache.org/tapestry/dtd/Script_3_0.dtd"},
                {"-//Apache Software Foundation//Tapestry Script Specification 4.0//EN", "http://tapestry.apache.org/dtd/Script_4_0.dtd"}};
        for (int i = 0; i < doctypes.length; i++) {
            final PsiFile file = configure("Test" + i + ".script", """
                    <?xml version="1.0"?>
                    <!DOCTYPE script PUBLIC "%s" "%s">
                    <script>
                      <input-symbol key="name" required="yes"/>
                      <body/>
                      <unknown-element/>
                    </script>
                    """.formatted(doctypes[i][0], doctypes[i][1]));
            final List<String> children = allowedChildren(rootTag(file));
            assertTrue(doctypes[i][0] + " " + children,
                    children.containsAll(List.of("include-script", "input-symbol", "body", "initialization")));
            assertTrue(doctypes[i][0], hasErrorOn());
        }
    }

    public void testSpecsForClass() {
        final PsiClass home = myFixture.findClass("com.example.Home");
        final List<XmlFile> specs = TapestryModel.findSpecsForClass(home);
        assertEquals(1, specs.size());
        assertEquals("Home.page", specs.getFirst().getName());
    }
}
