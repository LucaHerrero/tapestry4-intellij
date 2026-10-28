package com.herreromarcos.idea.tapestry4plugin;

import java.util.List;

/**
 * Regeln aus dem Abgleich mit dem Quellcode von Tapestry 4.1.6 (SpecificationParser, ComponentSpecification,
 * LibrarySpecification, PageLoader, ComponentTemplateLoaderLogic).
 */
public class Tapestry41RulesTest extends TapestryTestCase {

    private static long count(List<String> messages, String message) {
        return messages.stream().filter(message::equals).count();
    }

    private void addLegacyComponent() {
        myFixture.addFileToProject("Legacy.jwc", component("""
                <component-specification>
                  <parameter name="value" aliases="selected"/>
                </component-specification>
                """));
    }

    // ---- Abweichungen bestehender Prüfungen

    public void testReservedParameterIsAnErrorInTheTemplate() {
        myFixture.addFileToProject("Link.jwc", component("""
                <component-specification><reserved-parameter name="href"/></component-specification>
                """));
        final List<String> errors = errors("Home.html", """
                <html><body><a jwcid="@Link" href="#">wysiwyg</a><a jwcid="@Link" href="ognl:userName">x</a></body></html>
                """);
        assertEquals(errors.toString(), 1, count(errors, "'href' is reserved by component 'Link' and must not be bound in the template"));
    }

    public void testReservedParameterIsOnlyAWarningInTheSpecification() {
        myFixture.addFileToProject("Link.jwc", component("""
                <component-specification><reserved-parameter name="href"/></component-specification>
                """));
        final List<String> warnings = warnings("Reserved.page", page("""
                <page-specification>
                  <component id="link" type="Link"><binding name="href" value="userName"/></component>
                </page-specification>
                """));
        assertTrue(warnings.toString(), warnings.contains("'href' is reserved by component 'Link' and will be ignored"));
        assertFalse(warnings.toString(), errors().stream().anyMatch(m -> m.contains("reserved")));
    }

    public void testFormalOnlyComponentReportsInformalBeforeReserved() {
        myFixture.addFileToProject("StrictLink.jwc", component("""
                <component-specification allow-informal-parameters="no"><reserved-parameter name="href"/></component-specification>
                """));
        final List<String> errors = errors("Home.html", "<html><body><a jwcid=\"@StrictLink\" href=\"ognl:userName\">x</a></body></html>");
        assertEquals(errors.toString(), 1, count(errors, "Component 'StrictLink' does not allow informal parameters"));
        assertFalse(errors.toString(), errors.stream().anyMatch(m -> m.contains("reserved")));
    }

    public void testUnboundRequiredParameterIsAnError() {
        final List<String> errors = errors("Home.html", "<html><body><span jwcid=\"@Insert\"/></body></html>");
        assertEquals(errors.toString(), 1, count(errors, "Required parameter 'value' of component 'Insert' is not bound"));
    }

    // ---- Spezifikationen: <component>, Parameter

    public void testComponentWithTypeAndCopyOf() {
        final List<String> errors = errors("Both.page", page("""
                <page-specification>
                  <component id="a" type="Insert"><binding name="value" value="1"/></component>
                  <component id="b" type="Insert" copy-of="a"/>
                </page-specification>
                """));
        assertEquals(errors.toString(), 1, count(errors, "Component must not have both a type and a copy-of attribute"));
    }

    public void testCopyOfNeedsAnEarlierComponent() {
        final List<String> errors = errors("CopyOrder.page", page("""
                <page-specification>
                  <component id="early" copy-of="late"/>
                  <component id="late" type="Insert"><binding name="value" value="1"/></component>
                  <component id="fine" copy-of="late"/>
                </page-specification>
                """));
        assertEquals(errors.toString(), 1, count(errors, "Component 'late' must be declared earlier in this file to be copied"));
    }

    public void testRequiredParameterWithDefaultValue() {
        final List<String> errors = errors("Defaults.jwc", component("""
                <component-specification>
                  <parameter name="a" required="yes" default-value="literal:x"/>
                  <parameter name="b" default-value="literal:x"/>
                  <parameter name="c" required="yes"/>
                </component-specification>
                """));
        assertEquals(errors.toString(), 1, count(errors, "Required parameter 'a' must not have a default value"));
        assertEquals(errors.toString(), 1, errors.stream().filter(m -> m.contains("must not have a default value")).count());
    }

    public void testRequiredParameterAnnotationWithDefaultValue() {
        addAnnotation("Parameter", "boolean required() default false; String defaultValue() default \"\"; String name() default \"\";");
        final List<String> errors = errors("com/example/Widget.java", """
                package com.example;
                import org.apache.tapestry.annotations.Parameter;
                public abstract class Widget {
                    @Parameter(required = true, defaultValue = "literal:x") public abstract String getLabel();
                    @Parameter(defaultValue = "literal:x") public abstract String getTitle();
                }
                """);
        assertEquals(errors.toString(), 1, count(errors, "Required parameter 'label' must not have a default value"));
    }

    public void testInheritInformalParameters() {
        myFixture.addFileToProject("Strict.jwc", component("""
                <component-specification allow-informal-parameters="no"/>
                """));
        assertTrue(errors("Inherit.page", page("""
                <page-specification>
                  <component id="strict" type="Strict" inherit-informal-parameters="yes"/>
                </page-specification>
                """)).contains("Component 'Strict' allows only formal parameters and cannot inherit informal parameters"));
        assertTrue(errors("FormalOnly.jwc", component("""
                <component-specification allow-informal-parameters="no">
                  <component id="text" type="Insert" inherit-informal-parameters="yes"><binding name="value" value="1"/></component>
                </component-specification>
                """)).contains("FormalOnly.jwc allows only formal parameters, so its components cannot inherit informal parameters"));
        final List<String> fine = errors("InheritFine.jwc", component("""
                <component-specification>
                  <component id="text" type="Insert" inherit-informal-parameters="yes"><binding name="value" value="1"/></component>
                </component-specification>
                """));
        assertFalse(fine.toString(), fine.stream().anyMatch(m -> m.contains("inherit informal")));
    }

    public void testParameterBoundUnderNameAndAliasInSpecification() {
        addLegacyComponent();
        final List<String> errors = errors("Alias.page", page("""
                <page-specification>
                  <component id="legacy" type="Legacy">
                    <binding name="value" value="1"/>
                    <binding name="selected" value="2"/>
                  </component>
                </page-specification>
                """));
        assertEquals(errors.toString(), 1, count(errors, "Parameter 'selected' of component 'Legacy' is already bound as 'value'"));
    }

    // ---- Doppelte Deklarationen

    public void testDuplicateDeclarationsInComponentSpecification() {
        final List<String> errors = errors("Duplicates.jwc", component("""
                <component-specification>
                  <parameter name="value"/>
                  <parameter name="value"/>
                  <parameter name="other" aliases="value"/>
                  <asset name="logo" path="a.png"/>
                  <asset name="logo" path="b.png"/>
                  <bean name="map" class="java.util.HashMap"/>
                  <bean name="map" class="java.util.HashMap"/>
                  <property name="counter"/>
                  <property name="counter"/>
                  <property name="title"/>
                  <inject property="title" object="infrastructure:request"/>
                  <component id="link" type="Insert" property="map"><binding name="value" value="1"/></component>
                </component-specification>
                """));
        assertEquals(errors.toString(), 2, count(errors, "Parameter 'value' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Asset 'logo' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Bean 'map' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Property 'counter' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Property 'title' is already defined by <property>"));
        assertEquals(errors.toString(), 0, errors.stream().filter(m -> m.contains("'map' is already defined")).count());
    }

    public void testParameterClaimsItsProperty() {
        final List<String> errors = errors("Claims.jwc", component("""
                <component-specification>
                  <parameter name="value"/>
                  <property name="value"/>
                  <parameter name="label" property="caption"/>
                  <property name="label"/>
                </component-specification>
                """));
        assertEquals(errors.toString(), 1, count(errors, "Property 'value' is already defined by <parameter>"));
        assertFalse(errors.toString(), errors.stream().anyMatch(m -> m.contains("'label'")));
    }

    public void testDuplicateDeclarationsInApplication() {
        final List<String> errors = errors("App.application", DOCTYPE_4_0.formatted("application") + """
                <application name="app">
                  <page name="Home" specification-path="Home.page"/>
                  <page name="Home" specification-path="Other.page"/>
                  <component-type type="Border" specification-path="Border.jwc"/>
                  <component-type type="Border" specification-path="Border2.jwc"/>
                  <library id="contrib" specification-path="/org/apache/tapestry/contrib/Contrib.library"/>
                  <library id="contrib" specification-path="/org/apache/tapestry/contrib/Contrib.library"/>
                  <extension name="ext" class="java.util.HashMap"/>
                  <extension name="ext" class="java.util.HashMap"/>
                </application>
                """);
        assertEquals(errors.toString(), 1, count(errors, "Page 'Home' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Component type 'Border' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Library 'contrib' has already been declared"));
        assertEquals(errors.toString(), 1, count(errors, "Extension 'ext' has already been declared"));
    }

    // ---- Namensmuster und reservierte Library-ids

    public void testNamePatternsInComponentSpecification() {
        final List<String> errors = errors("Patterns.jwc", component("""
                <component-specification>
                  <property name="my-prop"/>
                  <property name="okProp"/>
                  <inject property="2nd" object="infrastructure:request"/>
                  <component id="a" type="lib:sub/Insert"/>
                  <component id="b" type="lib:sub:Insert"/>
                  <component id="c" type="Insert"><binding name="data-x" value="1"/></component>
                </component-specification>
                """));
        assertTrue(errors.toString(), errors.contains("Invalid property name 'my-prop': must be a Java identifier"));
        assertTrue(errors.toString(), errors.contains("Invalid property name '2nd': must be a Java identifier"));
        assertTrue(errors.toString(), errors.contains(
                "Invalid component type 'lib:sub:Insert': must be Java identifiers separated by '/', optionally with a library prefix ('contrib:Table')"));
        assertTrue(errors.toString(), errors.contains("Invalid parameter name 'data-x': must be a Java identifier"));
        assertEquals(errors.toString(), 4, errors.stream().filter(m -> m.startsWith("Invalid")).count());
    }

    public void testNamePatternsInApplication() {
        final List<String> errors = errors("Names.application", DOCTYPE_4_0.formatted("application") + """
                <application name="app">
                  <page name="admin/edit-user" specification-path="a.page"/>
                  <page name="my-admin/Edit" specification-path="b.page"/>
                  <component-type type="common/Border" specification-path="Border.jwc"/>
                  <component-type type="lib:Border" specification-path="Border.jwc"/>
                  <library id="my-lib" specification-path="/a.library"/>
                  <library id="framework" specification-path="/b.library"/>
                  <extension name="my-ext.part_2" class="java.util.HashMap"/>
                  <extension name="ext..x" class="java.util.HashMap"/>
                </application>
                """);
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.startsWith("Invalid page name 'my-admin/Edit'")));
        assertTrue(errors.toString(), errors.contains("Invalid component type 'lib:Border': must be Java identifiers separated by '/'"));
        assertTrue(errors.toString(), errors.contains("Invalid library id 'my-lib': must be a Java identifier"));
        assertTrue(errors.toString(), errors.contains("Library id 'framework' is reserved by Tapestry"));
        assertTrue(errors.toString(), errors.stream().anyMatch(m -> m.startsWith("Invalid extension name 'ext..x'")));
        assertEquals(errors.toString(), 4, errors.stream().filter(m -> m.startsWith("Invalid")).count());
    }

    public void testTapestry30PropertyIsMeta() {
        final List<String> errors = errors("Old.page", """
                <?xml version="1.0"?>
                <!DOCTYPE page-specification PUBLIC "-//Apache Software Foundation//Tapestry Specification 3.0//EN"
                  "http://jakarta.apache.org/tapestry/dtd/Tapestry_3_0.dtd">
                <page-specification>
                  <property name="org.apache.tapestry.template-extension" value="xhtml"/>
                  <property name="org.apache.tapestry.template-extension" value="xhtml"/>
                  <component id="any" type="Insert"><binding name="data-x" expression="'y'"/></component>
                  <property-specification name="counter" type="int"/>
                  <property-specification name="counter" type="int"/>
                </page-specification>
                """);
        assertFalse(errors.toString(), errors.stream().anyMatch(m -> m.contains("template-extension")));
        // enterBinding30 prüft keine Binding-Namen
        assertFalse(errors.toString(), errors.stream().anyMatch(m -> m.contains("data-x")));
        assertEquals(errors.toString(), 1, count(errors, "Property 'counter' has already been declared"));
    }

    // ---- Templates

    public void testImplicitIdAlreadyDeclaredInSpecification() {
        final List<String> errors = errors("Home.html", "<html><body><span jwcid=\"greeting@Insert\" value=\"ognl:userName\"/></body></html>");
        assertEquals(errors.toString(), 1, count(errors, "Component 'greeting' is already declared in Home.page"));
    }

    public void testParameterBoundUnderNameAndAliasInTemplate() {
        addLegacyComponent();
        final List<String> both = errors("Home.html",
                "<html><body><span jwcid=\"@Legacy\" value=\"ognl:userName\" selected=\"ognl:userName\"/></body></html>");
        assertEquals(both.toString(), 2, count(both, "Parameter 'value' of component 'Legacy' is bound more than once (value, selected)"));
        final List<String> preview = errors("Other.html",
                "<html><body><span jwcid=\"@Legacy\" value=\"ognl:userName\" selected=\"preview\"/></body></html>");
        assertEquals(preview.toString(), 1, count(preview, "Parameter 'value' of component 'Legacy' is bound more than once (value, selected)"));
    }
}
