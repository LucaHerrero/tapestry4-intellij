package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.psi.Property;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlAttributeValue;

import java.util.List;

/**
 * Abgleich mit der offiziellen Tapestry-4.0.2-Dokumentation (User's Guide, Annotations, Komponenten-Referenz)
 * und den Abweichungen der 4.1-Dokumentation. Jeder Test nennt das Kapitel, aus dem die Regel stammt.
 */
public class DocumentationComplianceTest extends TapestryTestCase {

    // ---- "Component Bindings": validator:, meta: und projekteigene Präfixe

    public void testValidatorAndMetaPrefixesAreKnown() {
        myFixture.addFileToProject("Bid.html", "<html></html>");
        final List<String> warnings = warnings("Bid.page", page("""
                <page-specification class="com.example.Home">
                  <component id="field" type="Insert">
                    <binding name="value" value="validator:string,required,minimumLength=3"/>
                    <binding name="raw" value="meta:renderIfTags"/>
                    <binding name="class" value="clientId:field"/>
                  </component>
                </page-specification>
                """));
        assertTrue(warnings.toString(), warnings.isEmpty());
    }

    public void testCustomBindingPrefixIsNotTreatedAsOgnl() {
        final List<String> warnings = warnings("Custom.page", page("""
                <page-specification class="com.example.Home">
                  <component id="x" type="Insert"><binding name="value" value="spring:userService"/></component>
                </page-specification>
                """));
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("'spring'")));
    }

    // ---- "Configuring Tapestry": org.apache.tapestry.default-binding-prefix

    public void testDefaultBindingPrefixFromSpecificationMeta() {
        final List<String> warnings = warnings("Literal.page", page("""
                <page-specification class="com.example.Home">
                  <meta key="org.apache.tapestry.default-binding-prefix" value="literal"/>
                  <component id="x" type="Insert"><binding name="value" value="disabled"/></component>
                </page-specification>
                """));
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("'disabled'")));
    }

    public void testDefaultBindingPrefixFromApplication() {
        myFixture.addFileToProject("App.application", DOCTYPE_4_0.formatted("application") + """
                <application><meta key="org.apache.tapestry.default-binding-prefix" value="literal"/></application>
                """);
        final List<String> warnings = warnings("Literal2.page", page("""
                <page-specification class="com.example.Home">
                  <component id="x" type="Insert"><binding name="value" value="someText"/></component>
                </page-specification>
                """));
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("'someText'")));
    }

    public void testWithoutConfigurationSpecBindingIsOgnl() {
        final List<String> warnings = warnings("Ognl.page", page("""
                <page-specification class="com.example.Home">
                  <component id="x" type="Insert"><binding name="value" value="unknownProperty"/></component>
                </page-specification>
                """));
        assertTrue(warnings.toString(), warnings.contains("Property 'unknownProperty' not found in Home"));
    }

    // ---- "<parameter> element": default-value ist eine Binding-Referenz (Standard OGNL)

    public void testParameterDefaultValueIsOgnl() {
        configure("Dv.jwc", component("""
                <component-specification class="com.example.Home">
                  <parameter name="title" default-value="user<caret>Name"/>
                </component-specification>
                """));
        assertEquals("getUserName", ((com.intellij.psi.PsiMethod) resolveAtCaret()).getName());
    }

    // ---- "<bean> element" + Konfiguration: lightweight initialization und bean-class-packages

    public void testBeanClassWithLightweightInitialization() {
        myFixture.addFileToProject("com/example/StringValidator.java", "package com.example; public class StringValidator {}");
        final List<String> problems = warnings("Bean.page", page("""
                <page-specification>
                  <bean name="v" class="com.example.StringValidator,required,minimumLength=10"/>
                </page-specification>
                """));
        assertTrue(problems.toString(), problems.isEmpty());
    }

    public void testBeanClassViaBeanClassPackages() {
        myFixture.addFileToProject("com/example/beans/Helper.java", "package com.example.beans; public class Helper {}");
        myFixture.addFileToProject("App.application", DOCTYPE_4_0.formatted("application") + """
                <application><meta key="org.apache.tapestry.bean-class-packages" value="com.example.beans"/></application>
                """);
        configure("Bean2.page", page("""
                <page-specification><bean name="h" class="Hel<caret>per"/></page-specification>
                """));
        assertEquals("com.example.beans.Helper", ((PsiClass) resolveAtCaret()).getQualifiedName());
    }

    // ---- Annotations: @Asset, @Bean, @ComponentClass

    public void testAnnotatedAssetAndBean() {
        myFixture.addFileToProject("com/example/Annotated.java", """
                package com.example;
                public abstract class Annotated {
                    @org.apache.tapestry.annotations.Asset("/style/global.css") public abstract Object getGlobalStylesheet();
                    @org.apache.tapestry.annotations.Bean public abstract java.util.HashMap getHashMapBean();
                }
                """);
        myFixture.addFileToProject("Annotated.page", page("<page-specification class=\"com.example.Annotated\"/>"));
        final List<String> warnings = warnings("Annotated.html", """
                <html><body>
                <span jwcid="@Insert" value="asset:globalStylesheet"/>
                <span jwcid="@Insert" value="bean:hashMapBean"/>
                </body></html>
                """);
        assertTrue(warnings.toString(), warnings.isEmpty());
    }

    public void testComponentClassAnnotationControlsInformalParameters() {
        myFixture.addFileToProject("org/apache/tapestry/annotations/ComponentClass.java", """
                package org.apache.tapestry.annotations;
                public @interface ComponentClass { boolean allowBody() default true; boolean allowInformalParameters() default true; }
                """);
        myFixture.addFileToProject("com/example/Strict.java", """
                package com.example;
                @org.apache.tapestry.annotations.ComponentClass(allowInformalParameters = false)
                public abstract class Strict {}
                """);
        myFixture.addFileToProject("Strict.jwc", component("<component-specification class=\"com.example.Strict\"/>"));
        final List<String> errors = warnings("UsesStrict.page", page("""
                <page-specification>
                  <component id="s" type="Strict"><binding name="foo" value="literal:x"/></component>
                </page-specification>
                """));
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.contains("does not allow informal parameters")));
    }

    // ---- "Boolean type values"

    public void testBooleanValuesOnAndOne() {
        myFixture.addFileToProject("Req.jwc", component("""
                <component-specification><parameter name="a" required="on"/><parameter name="b" required="1"/></component-specification>
                """));
        final List<String> warnings = warnings("UsesReq.html", "<html><body><span jwcid=\"@Req\"/></body></html>");
        assertTrue(warnings.toString(), warnings.contains("Required parameter 'a' of component 'Req' is not bound"));
        assertTrue(warnings.toString(), warnings.contains("Required parameter 'b' of component 'Req' is not bound"));
    }

    // ---- "Listener Methods": immer public

    public void testPrivateListenerMethodIsReported() {
        myFixture.addFileToProject("com/example/Lst.java", """
                package com.example;
                public abstract class Lst { private void doHidden() {} }
                """);
        myFixture.addFileToProject("Lst.page", page("<page-specification class=\"com.example.Lst\"/>"));
        final List<String> errors = warnings("Lst.html",
                "<html><body><a jwcid=\"@DirectLink\" listener=\"listener:doHidden\">x</a></body></html>");
        assertTrue(errors.toString(), errors.contains("Listener method 'doHidden' in Lst must be a public instance method"));
    }

    // ---- "Determining the Page Class": Ordner im Seitennamen und Default-Paket

    public void testPageClassWithFolderInPageName() {
        myFixture.addFileToProject("webapp/WEB-INF/web.xml", "<web-app/>");
        myFixture.addFileToProject("webapp/WEB-INF/App.application", DOCTYPE_4_0.formatted("application") + """
                <application><meta key="org.apache.tapestry.page-class-packages" value="org.example.pages"/></application>
                """);
        myFixture.addFileToProject("org/example/pages/admin/EditUser.java",
                "package org.example.pages.admin; public abstract class EditUser {}");
        final PsiFile spec = myFixture.addFileToProject("webapp/WEB-INF/admin/EditUser.page", page("<page-specification/>"));
        final PsiClass cls = TapestryModel.getContext(spec).declaredClass();
        assertNotNull(cls);
        assertEquals("org.example.pages.admin.EditUser", cls.getQualifiedName());
    }

    // ---- "Localization": lokalisierte Templates, Namespace-Katalog, <span key>

    public void testLocalizedTemplateUsesBaseSpecification() {
        final PsiFile template = myFixture.addFileToProject("Home_de.html", "<html></html>");
        final PsiFile spec = TapestryModel.getContext(template).spec();
        assertNotNull(spec);
        assertEquals("Home.page", spec.getName());
    }

    public void testServletNamespaceCatalogWithoutApplicationSpec() {
        myFixture.addFileToProject("web/WEB-INF/web.xml", """
                <web-app><servlet><servlet-name>myapp</servlet-name>
                <servlet-class>org.apache.tapestry.ApplicationServlet</servlet-class></servlet></web-app>
                """);
        myFixture.addFileToProject("web/WEB-INF/myapp.properties", "global-title=Hello\n");
        configure("web/Start.html", "<html><body><span jwcid=\"@Insert\" value=\"message:global-ti<caret>tle\"/></body></html>");
        assertInstanceOf(resolveAtCaret(), Property.class);
    }

    public void testSpanKeyDirective() {
        myFixture.enableInspections(new HtmlUnknownAttributeInspection());
        configure("Home.html", "<html><body><span key=\"ti<caret>tle\" raw=\"true\">Welcome</span></body></html>");
        assertInstanceOf(resolveAtCaret(), Property.class);
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("Unknown html attribute")));
    }

    // ---- "Templates": Komponenten in $remove$ und im verworfenen Body

    public void testComponentInsideRemoveBlock() {
        final List<String> errors = warnings("Home.html", """
                <html><body><tr jwcid="$remove$"><td><span jwcid="@Insert" value="ognl:userName"/></td></tr></body></html>
                """);
        assertTrue(errors.toString(), errors.contains("Components are not allowed inside a $remove$ block"));
    }

    public void testComponentInsideDiscardedBody() {
        final List<String> errors = warnings("Home.html", """
                <html><body><span jwcid="@Insert" value="ognl:userName"><span jwcid="@Insert" value="ognl:userName"/></span></body></html>
                """);
        assertTrue(errors.toString(), errors.contains("Component 'Insert' discards its body; components are not allowed inside it"));
    }

    // ---- "<asset> element": URL-Schemata sind keine Dateipfade

    public void testExternalAssetUrlIsNotAFile() {
        configure("Ext.page", page("""
                <page-specification><asset name="logo" path="http://example.com/logo.png"/></page-specification>
                """));
        final PsiElement value = myFixture.getFile().findElementAt(myFixture.getFile().getText().indexOf("http://example.com"));
        final XmlAttributeValue attribute = com.intellij.psi.util.PsiTreeUtil.getParentOfType(value, XmlAttributeValue.class);
        assertNotNull(attribute);
        assertFalse(java.util.Arrays.stream(attribute.getReferences()).anyMatch(r -> r instanceof com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference));
    }

    // ---- "Configuring Tapestry": jwcid-attribute-name und template-extension

    public void testConfiguredJwcidAttributeName() {
        myFixture.addFileToProject("App.application", DOCTYPE_4_0.formatted("application") + """
                <application><meta key="org.apache.tapestry.jwcid-attribute-name" value="tid"/></application>
                """);
        configure("Home.html", "<html><body><span tid=\"gree<caret>ting\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlAttributeValue.class);
        assertEquals("greeting", ((XmlAttributeValue) target).getValue());

        myFixture.enableInspections(new HtmlUnknownAttributeInspection());
        final List<String> warnings = warnings("Other.html", "<html><body><span tid=\"@Insert\"/></body></html>");
        assertTrue(warnings.toString(), warnings.contains("Required parameter 'value' of component 'Insert' is not bound"));
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("Unknown html attribute")));
    }

    public void testJwcidAttributeNameFromMetaAnnotation() {
        myFixture.addFileToProject("com/example/MetaPage.java", """
                package com.example;
                @org.apache.tapestry.annotations.Meta("org.apache.tapestry.jwcid-attribute-name=id")
                public abstract class MetaPage {}
                """);
        myFixture.addFileToProject("MetaPage.page", page("""
                <page-specification class="com.example.MetaPage"><component id="c" type="Insert"/></page-specification>
                """));
        configure("MetaPage.html", "<html><body><span id=\"<caret>c\"/></body></html>");
        assertInstanceOf(resolveAtCaret(), XmlAttributeValue.class);
    }

    public void testConfiguredTemplateExtension() {
        final PsiFile spec = myFixture.addFileToProject("Mobile.page", page("""
                <page-specification><meta key="org.apache.tapestry.template-extension" value="htm"/></page-specification>
                """));
        myFixture.addFileToProject("Mobile.html", "<html></html>");
        myFixture.addFileToProject("Mobile.htm", "<html></html>");
        final List<PsiFile> templates = TapestryModel.findTemplatesForSpec((com.intellij.psi.xml.XmlFile) spec);
        assertEquals(1, templates.size());
        assertEquals("Mobile.htm", templates.get(0).getName());
    }

    // ---- Tapestry 3.0-DTD wird von Tapestry 4 unterstützt

    public void testTapestry30Specification() {
        final String doctype30 = """
                <?xml version="1.0"?>
                <!DOCTYPE page-specification PUBLIC "-//Apache Software Foundation//Tapestry Specification 3.0//EN"
                  "http://jakarta.apache.org/tapestry/dtd/Tapestry_3_0.dtd">
                """;
        final PsiFile spec = myFixture.addFileToProject("Old.page", doctype30 + """
                <page-specification class="com.example.Home">
                  <property-specification name="counter" type="int"/>
                  <component id="greeting" type="Insert">
                    <static-binding name="value" value="Hello"/>
                  </component>
                </page-specification>
                """);
        assertTrue(TapestryFiles.isSpecFile(spec));
        assertSameElements(allowedChildren(rootTag(spec).findFirstSubTag("component")),
                // Original 3.0-DTD: <!ELEMENT component (property*, (binding | inherited-binding | listener-binding | static-binding | message-binding)*)>
                "property", "binding", "inherited-binding", "listener-binding", "message-binding", "static-binding");
        final List<String> warnings = warnings("Old.html", """
                <html><body><span jwcid="greeting"/><span jwcid="@Insert" value="ognl:counter"/></body></html>
                """);
        assertTrue(warnings.toString(), warnings.isEmpty());
    }
}
