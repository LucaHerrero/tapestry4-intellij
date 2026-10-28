package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;

import java.util.List;

/**
 * Abgleich mit dem Tapestry-4.1-User's-Guide und der Annotations-Referenz (tapestry.apache.org/tapestry4.1).
 * Jeder Test nennt das Kapitel, aus dem die Regel stammt.
 */
public class DocumentationAlignmentTest extends TapestryTestCase {

    // ---- "Templates": jwcid="id@Type" deklariert eine eingebettete Komponente

    public void testTemplateDeclaredComponentIsANestedComponent() {
        addAnnotation("InjectComponent", "String value();");
        addAnnotation("EventListener", "String[] events(); String[] targets() default {}; String submitForm() default \"\";");
        myFixture.addFileToProject("com/example/Nested.java", """
                package com.example;
                import org.apache.tapestry.annotations.*;
                public abstract class Nested {
                    @InjectComponent("field") public abstract Object getField();
                    @InjectComponent("missing") public abstract Object getMissing();
                    @EventListener(events = "onchange", targets = "field") public void changed() {}
                }
                """);
        myFixture.addFileToProject("Nested.page", page("<page-specification class=\"com.example.Nested\"/>"));
        configure("Nested.html", """
                <html><body>
                  <span jwcid="field@Insert" value="ognl:1"/>
                  <span jwcid="@Insert" value="component:field"/>
                  <span jwcid="@Insert" value="clientId:fi<caret>eld"/>
                </body></html>
                """);
        assertEmpty(errors());
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("field@Insert", ((XmlAttributeValue) target).getValue());

        myFixture.configureFromExistingVirtualFile(myFixture.findClass("com.example.Nested").getContainingFile().getVirtualFile());
        final List<String> javaErrors = errors();
        assertTrue(javaErrors.toString(), javaErrors.contains("Component 'missing' is not declared in Nested.page"));
        assertFalse(javaErrors.toString(), javaErrors.stream().anyMatch(m -> m.contains("'field'")));
    }

    public void testJwcidStillRequiresADeclaration() {
        configure("Home.html", "<html><body><span jwcid=\"field@Insert\" value=\"ognl:1\"/><span jwcid=\"field\"/></body></html>");
        assertTrue(errors().toString(), errors().contains("Component 'field' is not declared in Home.page"));
    }

    // ---- Annotations-Referenz, @Component: "type (default: return type)"

    public void testComponentTypeDefaultsToReturnType() {
        addAnnotation("Component", "String id() default \"\"; String type() default \"\"; String copyOf() default \"\"; String[] bindings() default {};");
        myFixture.addFileToProject("org/apache/tapestry/components/Insert.java",
                "package org.apache.tapestry.components; public abstract class Insert {}");
        myFixture.addFileToProject("com/example/Typed.java", """
                package com.example;
                public abstract class Typed {
                    @org.apache.tapestry.annotations.Component public abstract org.apache.tapestry.components.Insert getGreeting();
                }
                """);
        myFixture.addFileToProject("Typed.page", page("<page-specification class=\"com.example.Typed\"/>"));
        configure("Typed.html", "<html><body><span jwcid=\"greeting\"/></body></html>");
        final List<String> warnings = warnings();
        assertTrue(warnings.toString(), warnings.contains("Required parameter 'value' of component 'Insert' is not bound"));
    }

    // ---- "Specification DTD": <component> braucht type oder copy-of, Namen sind Java-Bezeichner

    public void testComponentNeedsTypeOrCopyOf() {
        final List<String> errors = warnings("NoType.page", page("""
                <page-specification class="com.example.Home">
                  <component id="untyped"/>
                  <component id="typed" type="Insert"><binding name="value" value="1"/></component>
                  <component id="copy" copy-of="typed"/>
                </page-specification>
                """));
        assertEquals(errors.toString(), 1, errors.stream().filter(m -> m.contains("either a type or a copy-of")).count());
    }

    public void testNamesMustBeJavaIdentifiers() {
        final List<String> errors = warnings("Names.jwc", component("""
                <component-specification>
                  <parameter name="my-param"/>
                  <parameter name="okParam"/>
                  <bean name="2nd" class="java.util.HashMap"/>
                  <asset name="$template" path="Other.html"/>
                  <asset name="logo.small" path="logo.png"/>
                </component-specification>
                """));
        assertTrue(errors.toString(), errors.contains("Invalid parameter name 'my-param': must be a Java identifier"));
        assertTrue(errors.toString(), errors.contains("Invalid bean name '2nd': must be a Java identifier"));
        assertTrue(errors.toString(), errors.contains("Invalid asset name 'logo.small': must be a Java identifier"));
        assertEquals(errors.toString(), 3, errors.stream().filter(m -> m.startsWith("Invalid")).count());
    }

    // ---- "Configuring Tapestry": Ausnahmen vom üblichen Suchpfad

    private void addWebXmlParameter(String name, String value) {
        myFixture.addFileToProject("web/WEB-INF/web.xml", """
                <web-app>
                  <context-param><param-name>%s</param-name><param-value>%s</param-value></context-param>
                </web-app>
                """.formatted(name, value));
    }

    public void testTemplateExtensionIgnoresWebXml() {
        addWebXmlParameter("org.apache.tapestry.template-extension", "htm");
        final PsiFile spec = myFixture.addFileToProject("web/WEB-INF/Ext.page", page("<page-specification/>"));
        myFixture.addFileToProject("web/Ext.htm", "<html></html>");
        myFixture.addFileToProject("web/Ext.html", "<html></html>");
        final List<PsiFile> templates = TapestryModel.findTemplatesForSpec((XmlFile) spec);
        assertEquals(1, templates.size());
        assertEquals("Ext.html", templates.getFirst().getName());
    }

    public void testClassPackagesMustBeConfiguredInTheApplication() {
        addWebXmlParameter("org.apache.tapestry.page-class-packages", "org.example.pages");
        myFixture.addFileToProject("org/example/pages/Admin.java", "package org.example.pages; public class Admin {}");
        final PsiFile template = myFixture.addFileToProject("web/Admin.html", "<html></html>");
        assertNull(TapestryModel.getContext(template).declaredClass());
    }

    public void testNamespacePropertiesName() {
        myFixture.addFileToProject("web/WEB-INF/shop.application", DOCTYPE_4_0.formatted("application") + """
                <application><meta key="org.apache.tapestry.namespace-properties-name" value="texts"/></application>
                """);
        myFixture.addFileToProject("web/WEB-INF/texts.properties", "shop-title=Shop\n");
        myFixture.addFileToProject("web/WEB-INF/shop.properties", "ignored-title=Ignored\n");
        myFixture.addFileToProject("web/WEB-INF/Cart.page", page("<page-specification/>"));
        configure("web/Cart.html", """
                <html><body>
                  <span jwcid="@Insert" value="message:shop-title"/>
                  <span jwcid="@Insert" value="message:ignored-title"/>
                </body></html>
                """);
        final List<String> warnings = warnings();
        assertFalse(warnings.toString(), warnings.contains("Message key 'shop-title' not found"));
        assertTrue(warnings.toString(), warnings.contains("Message key 'ignored-title' not found"));
    }

    // ---- "Property Injection" und "Component Bindings": Meta-Schlüssel

    public void testMetaKeyReferences() {
        addAnnotation("InjectMeta", "String value() default \"\";");
        myFixture.addFileToProject("com/example/Meta.java", """
                package com.example;
                public abstract class Meta {
                    @org.apache.tapestry.annotations.InjectMeta("page-size") public abstract int getPageSize();
                }
                """);
        final PsiFile spec = myFixture.addFileToProject("Meta.page", page("""
                <page-specification class="com.example.Meta">
                  <meta key="page-size" value="15"/>
                  <inject property="size" type="meta" object="page-size"/>
                </page-specification>
                """));
        configure("Meta.html", "<html><body><span jwcid=\"@Insert\" value=\"meta:page-<caret>size\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertSame(spec, target.getContainingFile());
        assertEmpty(warnings());
    }

    // ---- Annotations-Referenz: @Message, @InjectState, @InjectStateFlag ohne Wert

    public void testMessageKeyDerivedFromMethodName() {
        addAnnotation("Message", "String value() default \"\";");
        myFixture.addFileToProject("Derived.properties", "page-title=Title\n");
        myFixture.addFileToProject("com/example/Derived.java", """
                package com.example;
                import org.apache.tapestry.annotations.Message;
                public abstract class Derived {
                    @Message public abstract String getPageTitle();
                    @Message public abstract String getMissingText();
                }
                """);
        myFixture.addFileToProject("Derived.page", page("<page-specification class=\"com.example.Derived\"/>"));
        myFixture.configureFromExistingVirtualFile(myFixture.findClass("com.example.Derived").getContainingFile().getVirtualFile());
        final List<String> warnings = warnings();
        assertTrue(warnings.toString(), warnings.contains("Message key 'missing-text' (derived from getMissingText) not found"));
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("page-title")));
    }

    public void testStateObjectDerivedFromMethodName() {
        addAnnotation("InjectState", "String value() default \"\";");
        addAnnotation("InjectStateFlag", "String value() default \"\";");
        myFixture.addFileToProject("META-INF/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="tapestry.state.ApplicationObjects">
                    <state-object name="registration-data" scope="session"/>
                  </contribution>
                </module>
                """);
        myFixture.addFileToProject("com/example/Stateful.java", """
                package com.example;
                import org.apache.tapestry.annotations.*;
                public abstract class Stateful {
                    @InjectState public abstract Object getRegistrationData();
                    @InjectStateFlag public abstract boolean getRegistrationDataExists();
                    @InjectState public abstract Object getShoppingCart();
                }
                """);
        myFixture.addFileToProject("Stateful.page", page("<page-specification class=\"com.example.Stateful\"/>"));
        myFixture.configureFromExistingVirtualFile(myFixture.findClass("com.example.Stateful").getContainingFile().getVirtualFile());
        final List<String> warnings = warnings();
        assertTrue(warnings.toString(), warnings.contains("Unknown application state object 'shopping-cart' (derived from getShoppingCart)"));
        assertEquals(warnings.toString(), 1, warnings.stream().filter(m -> m.contains("application state object")).count());
    }
}
