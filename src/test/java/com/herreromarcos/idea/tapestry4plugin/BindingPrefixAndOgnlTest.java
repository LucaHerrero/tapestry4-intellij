package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Registrierte Binding-Präfixe (tapestry.bindings.BindingFactories) und OGNL-Ausdrücke über den OGNL-Parser. */
public class BindingPrefixAndOgnlTest extends TapestryTestCase {
    /** Echtes JDK der Test-JVM: Elementtypen (List, Map) und java.lang-Klassen brauchen die JDK-Klassen. */
    private static final LightProjectDescriptor WITH_JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("test-jdk", System.getProperty("java.home"), false));

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return WITH_JDK;
    }

    private void registerSpringPrefix() {
        myFixture.addFileToProject("META-INF/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="tapestry.bindings.BindingFactories">
                    <binding prefix="spring" service-id="app.SpringBindingFactory"/>
                  </contribution>
                </module>
                """);
    }

    // ---- Binding-Präfixe

    public void testCustomPrefixResolvesToRegistration() {
        registerSpringPrefix();
        configure("Home.html", "<html><body><span jwcid=\"@Insert\" value=\"spr<caret>ing:userService\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("spring", ((XmlAttributeValue) target).getValue());
    }

    public void testCustomPrefixIsNotEvaluatedAndOfferedInCompletion() {
        registerSpringPrefix();
        final List<String> warnings = warnings("Home.html",
                "<html><body><span jwcid=\"@Insert\" value=\"spring:no.such.thing\"/></body></html>");
        assertTrue(warnings.toString(), warnings.isEmpty());

        // "sp" passt nur auf das eigene Präfix → wird direkt eingefügt
        configure("Other.html", "<html><body><span jwcid=\"@Insert\" value=\"sp<caret>\"/></body></html>");
        myFixture.completeBasic();
        assertTrue(myFixture.getEditor().getDocument().getText(), myFixture.getEditor().getDocument().getText().contains("value=\"spring:"));
    }

    public void testUnregisteredPrefixInSpecificationIsReported() {
        registerSpringPrefix();
        final List<String> warnings = warnings("Bad.page", page("""
                <page-specification class="com.example.Home">
                  <component id="a" type="Insert"><binding name="value" value="sprung:userService"/></component>
                </page-specification>
                """));
        assertTrue(warnings.toString(), warnings.contains(
                "Unknown binding prefix 'sprung': Tapestry evaluates the whole value as OGNL expression"));
    }

    public void testUnregisteredPrefixInTemplateIsLiteral() {
        registerSpringPrefix();
        final List<String> warnings = warnings("Home.html",
                "<html><body><span jwcid=\"@Insert\" value=\"mailto:someone@example.com\"/></body></html>");
        assertTrue(warnings.toString(), warnings.isEmpty());
    }

    // ---- OGNL

    private void addOgnlClasses() {
        myFixture.addFileToProject("com/example/Util.java", """
                package com.example;
                public class Util {
                    public static final int MAX = 10;
                    public static String format(Object o) { return null; }
                }
                """);
        myFixture.addFileToProject("com/example/Catalog.java", """
                package com.example;
                public abstract class Catalog {
                    public abstract java.util.List<User> getUsers();
                    public abstract User[] getUserArray();
                    public abstract java.util.Map<String, User> getUserMap();
                    public abstract int getLimit();
                }
                """);
        myFixture.addFileToProject("Catalog.page", page("<page-specification class=\"com.example.Catalog\"/>"));
    }

    public void testComplexExpressionsAreChecked() {
        addOgnlClasses();
        final List<String> warnings = warnings("Catalog.html", """
                <html><body>
                  <span jwcid="@Insert" value="ognl:users.size > limit ? users[0].name : userArray[1].address.city"/>
                  <span jwcid="@Insert" value="ognl:userMap['x'].name + ' ' + users.{name}.size()"/>
                  <span jwcid="@Insert" value="ognl:@com.example.Util@format(users.isEmpty) + @com.example.Util@MAX"/>
                  <span jwcid="@Insert" value="ognl:#root.limit"/>
                  <span jwcid="@Insert" value="ognl:users[0].nam + missing"/>
                </body></html>
                """);
        assertEquals(warnings.toString(), List.of("Property 'nam' not found in User", "Property 'missing' not found in Catalog"),
                warnings.stream().filter(m -> m.startsWith("Property") || m.startsWith("Method") || m.startsWith("Static")).toList());
    }

    public void testIndexedElementTypeResolves() {
        addOgnlClasses();
        configure("Catalog.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:users[0].addr<caret>ess\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("getAddress", ((PsiMethod) target).getName());
    }

    public void testStaticFieldResolves() {
        addOgnlClasses();
        configure("Catalog.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:@com.example.Util@M<caret>AX\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiField.class);
        assertEquals("MAX", ((PsiField) target).getName());
    }

    public void testStaticMethodOfDefaultClassAndJavaLang() {
        addOgnlClasses();
        configure("Catalog.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:@@m<caret>ax(limit, 1)\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("java.lang.Math", ((PsiMethod) target).getContainingClass().getQualifiedName());
    }

    public void testUnknownStaticMemberIsReported() {
        addOgnlClasses();
        final List<String> warnings = warnings("Catalog.html",
                "<html><body><span jwcid=\"@Insert\" value=\"ognl:@com.example.Util@MIN\"/></body></html>");
        assertTrue(warnings.toString(), warnings.contains("Static field 'MIN' not found in Util"));
    }

    public void testSyntaxErrorIsReported() {
        addOgnlClasses();
        final List<String> warnings = warnings("Catalog.html",
                "<html><body><span jwcid=\"@Insert\" value=\"ognl:users.\"/></body></html>");
        assertTrue(warnings.toString(), warnings.contains("Invalid OGNL expression: unexpected end"));
    }
}
