package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.Nullable;

/**
 * Alles, was zu einer Seite bzw. Komponente gehört: Template, Spezifikation (.page/.jwc) und Java-Klasse.
 *
 * @param template      das HTML-Template (falls gefunden)
 * @param spec          die Spezifikation (.page/.jwc), fehlt bei spezifikationslosen Seiten
 * @param declaredClass die explizit (class-Attribut) oder per *-class-packages ermittelte Klasse
 * @param effectiveClass declaredClass oder ersatzweise BasePage/BaseComponent aus dem Classpath
 * @param kind          PAGE oder COMPONENT
 */
public record TapestryContext(@Nullable PsiFile template,
                              @Nullable XmlFile spec,
                              @Nullable PsiClass declaredClass,
                              @Nullable PsiClass effectiveClass,
                              SpecKind kind) {

    /** Sind überhaupt Informationen vorhanden, gegen die geprüft werden kann? */
    public boolean isKnown() {
        return spec != null || declaredClass != null;
    }

    public String displayName() {
        if (spec != null) return spec.getName();
        if (declaredClass != null) return declaredClass.getQualifiedName();
        return template != null ? template.getName() : "?";
    }
}
