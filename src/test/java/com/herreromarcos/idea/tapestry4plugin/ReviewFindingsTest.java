package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.herreromarcos.idea.tapestry4plugin.references.TapestryReference;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageInfo;

import java.util.Collection;
import java.util.List;

/** Regressionstests zu den Befunden aus dem Code-Review. */
public class ReviewFindingsTest extends TapestryTestCase {

    // ---- Rename / Find Usages: OGNL- und Listener-Referenzen müssen von Java aus gefunden werden

    public void testRenameGetterUpdatesOgnlInTemplate() {
        configure("Home.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:userName\"/></body></html>");
        final PsiMethod getter = myFixture.findClass("com.example.Home").findMethodsByName("getUserName", false)[0];
        myFixture.renameElement(getter, "getFullName");
        assertTrue(myFixture.getFile().getText(), myFixture.getFile().getText().contains("ognl:fullName"));
    }

    public void testFindUsagesOfGetterFindsOgnl() {
        configure("Home.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:userName\"/></body></html>");
        final PsiMethod getter = myFixture.findClass("com.example.Home").findMethodsByName("getUserName", false)[0];
        final Collection<UsageInfo> usages = myFixture.findUsages(getter);
        assertTrue(usages.toString(), usages.stream().anyMatch(u -> u.getReference() instanceof TapestryReference));
    }

    public void testFindUsagesOfListenerMethod() {
        configure("Home.html", "<html><body><a jwcid=\"@DirectLink\" listener=\"listener:onSave\">x</a></body></html>");
        final PsiMethod listener = myFixture.findClass("com.example.Home").findMethodsByName("onSave", false)[0];
        final Collection<UsageInfo> usages = myFixture.findUsages(listener);
        assertTrue(usages.toString(), usages.stream().anyMatch(u -> u.getReference() instanceof TapestryReference));
    }

    // ---- Pflichtparameter: inherited-binding und copy-of zählen als gebunden

    public void testRequiredParameterBoundViaInheritedBinding() {
        myFixture.addFileToProject("Inh.page", page("""
                <page-specification>
                  <component id="x" type="Insert"><inherited-binding name="value" parameter-name="text"/></component>
                </page-specification>
                """));
        configure("Inh.html", "<html><body><span jwcid=\"x\"/></body></html>");
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.startsWith("Required parameter")));
    }

    public void testRequiredParameterBoundViaCopyOf() {
        myFixture.addFileToProject("Copy.page", page("""
                <page-specification>
                  <component id="a" type="Insert"><binding name="value" value="literal:x"/></component>
                  <component id="b" copy-of="a"/>
                </page-specification>
                """));
        configure("Copy.html", "<html><body><span jwcid=\"a\"/><span jwcid=\"b\"/></body></html>");
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertFalse(warnings.toString(), warnings.stream().anyMatch(m -> m.startsWith("Required parameter")));
    }

    // ---- Message-Kataloge: Home_Admin.properties gehört zur Seite Home_Admin, nicht zu Home

    public void testMessageCatalogOfOtherPageIsIgnored() {
        myFixture.addFileToProject("Home_Admin.properties", "adminOnly=x\n");
        myFixture.addFileToProject("Home_de.properties", "germanOnly=y\n");
        configure("Home.html", "<html><body><span jwcid=\"@Insert\" value=\"message:adminOnly\"/>"
                + "<span jwcid=\"@Insert\" value=\"message:germanOnly\"/></body></html>");
        final List<String> warnings = highlightingMessages(HighlightSeverity.WARNING);
        assertTrue(warnings.toString(), warnings.contains("Message key 'adminOnly' not found"));
        assertFalse(warnings.toString(), warnings.contains("Message key 'germanOnly' not found"));
    }

    // ---- Java-EE application.xml ist keine Tapestry-Spezifikation

    public void testJavaEeApplicationXmlIsNotASpecification() {
        final PsiFile file = myFixture.addFileToProject("META-INF/application.xml", """
                <application xmlns="http://java.sun.com/xml/ns/javaee" version="5">
                  <module><web><web-uri>web.war</web-uri><context-root>component:x</context-root></web></module>
                </application>
                """);
        assertFalse(TapestryFiles.isSpecFile(file));
    }

    // ---- Spezifikationslose Komponente: Klasse über component-class-packages

    public void testSpeclessComponentClassViaComponentPackages() {
        myFixture.addFileToProject("App.application", DOCTYPE_4_0.formatted("application") + """
                <application>
                  <meta key="org.apache.tapestry.component-class-packages" value="com.example.components"/>
                </application>
                """);
        myFixture.addFileToProject("com/example/components/Widget.java",
                "package com.example.components; public abstract class Widget {}");
        final PsiFile template = myFixture.addFileToProject("Widget.html", "<div jwcid=\"$content$\"></div>");
        assertNotNull(TapestryModel.getContext(template).declaredClass());
    }

    // ---- Neue Aktion: ungültiger Klassenname darf kein kaputtes XML erzeugen (siehe NewTapestryElementAction)
}
