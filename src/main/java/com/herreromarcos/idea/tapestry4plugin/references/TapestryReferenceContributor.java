package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

/** Registriert die Referenzen in HTML-Templates, Tapestry-Spezifikationen und Script-Spezifikationen. */
public class TapestryReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
        // Höhere Priorität: bei "asset:logo" o.ä. soll nicht die HTML-URL-Referenz (src/href) gewinnen.
        // Die Provider liefern nur bei Tapestry-Ausdrücken Referenzen, HTML-Referenzen bleiben sonst unberührt.
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), new TemplateReferenceProvider(), PsiReferenceRegistrar.HIGHER_PRIORITY);
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), new SpecReferenceProvider(), PsiReferenceRegistrar.HIGHER_PRIORITY);
        registrar.registerReferenceProvider(XmlPatterns.xmlAttributeValue(), new ScriptReferenceProvider());
    }
}
