package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.xml.XmlFile;

import java.util.List;

/**
 * Suchpfad der Konfiguration wie in tapestry.props: Spezifikation → Namespace (.library bzw. .application) →
 * global (Servlet-init-param, context-param, hivemind.ApplicationDefaults).
 */
public class ConfigurationSourcesTest extends TapestryTestCase {

    private void addLibrary() {
        myFixture.addFileToProject("lib/My.library", DOCTYPE_4_0.formatted("library-specification") + """
                <library-specification>
                  <meta key="org.apache.tapestry.component-class-packages" value="com.lib"/>
                </library-specification>
                """);
        myFixture.addFileToProject("App.application", DOCTYPE_4_0.formatted("application") + """
                <application>
                  <meta key="org.apache.tapestry.component-class-packages" value="com.app"/>
                  <library id="my" specification-path="lib/My.library"/>
                </application>
                """);
        myFixture.addFileToProject("com/lib/Widget.java", """
                package com.lib;
                public abstract class Widget { public abstract String getLabel(); }
                """);
        myFixture.addFileToProject("com/app/Widget.java", """
                package com.app;
                public abstract class Widget { public abstract String getTitle(); }
                """);
        myFixture.addFileToProject("lib/Widget.jwc", component("<component-specification/>"));
    }

    public void testLibraryMetaDeterminesComponentClass() {
        addLibrary();
        configure("lib/Widget.html", "<div jwcid=\"$content$\"><span jwcid=\"@Insert\" value=\"ognl:la<caret>bel\"/></div>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("com.lib.Widget", ((PsiMethod) target).getContainingClass().getQualifiedName());
    }

    public void testApplicationMetaOutsideOfLibrary() {
        addLibrary();
        myFixture.addFileToProject("Widget.jwc", component("<component-specification/>"));
        configure("Widget.html", "<div jwcid=\"$content$\"><span jwcid=\"@Insert\" value=\"ognl:ti<caret>tle\"/></div>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, PsiMethod.class);
        assertEquals("com.app.Widget", ((PsiMethod) target).getContainingClass().getQualifiedName());
    }

    public void testJwcidAttributeFromServletInitParameter() {
        myFixture.addFileToProject("web/WEB-INF/web.xml", """
                <web-app>
                  <servlet>
                    <servlet-name>app</servlet-name>
                    <servlet-class>org.apache.tapestry.ApplicationServlet</servlet-class>
                    <init-param>
                      <param-name>org.apache.tapestry.jwcid-attribute-name</param-name>
                      <param-value>tid</param-value>
                    </init-param>
                  </servlet>
                </web-app>
                """);
        configure("web/Start.html", "<html><body><span tid=\"@Ins<caret>ert\" value=\"ognl:userName\"/></body></html>");
        final PsiElement target = resolveAtCaret();
        assertInstanceOf(target, XmlFile.class);
        assertEquals("Insert.jwc", ((XmlFile) target).getName());
    }

    /** Servlet-init-param vor context-param (tapestry.props.GlobalPropertySources). */
    public void testInitParameterWinsOverContextParameter() {
        myFixture.addFileToProject("web/WEB-INF/web.xml", """
                <web-app>
                  <context-param>
                    <param-name>org.apache.tapestry.jwcid-attribute-name</param-name>
                    <param-value>cid</param-value>
                  </context-param>
                  <servlet>
                    <servlet-name>app</servlet-name>
                    <init-param>
                      <param-name>org.apache.tapestry.jwcid-attribute-name</param-name>
                      <param-value>tid</param-value>
                    </init-param>
                  </servlet>
                </web-app>
                """);
        configure("web/Start.html", "<html><body><span tid=\"@Ins<caret>ert\" cid=\"@Insert\" value=\"literal:x\"/></body></html>");
        assertInstanceOf(resolveAtCaret(), XmlFile.class);
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.contains("cid")));
    }

    public void testSpecificationMetaWinsOverGlobal() {
        myFixture.addFileToProject("META-INF/hivemodule.xml", """
                <module id="app" version="1.0.0">
                  <contribution configuration-id="hivemind.ApplicationDefaults">
                    <default symbol="org.apache.tapestry.default-binding-prefix" value="literal"/>
                  </contribution>
                </module>
                """);
        final List<String> withGlobalLiteral = warnings("Lit.page", page("""
                <page-specification class="com.example.Home">
                  <component id="a" type="Insert"><binding name="value" value="noSuchProperty"/></component>
                </page-specification>
                """));
        assertFalse(withGlobalLiteral.toString(), withGlobalLiteral.stream().anyMatch(m -> m.contains("noSuchProperty")));

        final List<String> withSpecOgnl = warnings("Ognl.page", page("""
                <page-specification class="com.example.Home">
                  <meta key="org.apache.tapestry.default-binding-prefix" value="ognl"/>
                  <component id="a" type="Insert"><binding name="value" value="noSuchProperty"/></component>
                </page-specification>
                """));
        assertTrue(withSpecOgnl.toString(), withSpecOgnl.stream().anyMatch(m -> m.contains("'noSuchProperty'")));
    }
}
