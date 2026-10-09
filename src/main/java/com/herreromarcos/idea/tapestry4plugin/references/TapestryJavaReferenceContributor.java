package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.patterns.PsiJavaPatterns;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceRegistrar;
import org.jetbrains.annotations.NotNull;

/** Registriert die Referenzen in String-Werten der Tapestry-Annotationen (Java). */
public class TapestryJavaReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull final PsiReferenceRegistrar registrar) {
        // Alle Literale in Annotationen – insideAnnotationParam(...) träfe nur den Standardparameter "value",
        // gebraucht werden auch type/copyOf/bindings. Der Provider filtert selbst auf Tapestry-Annotationen.
        registrar.registerReferenceProvider(PsiJavaPatterns.literalExpression().inside(PsiJavaPatterns.psiAnnotation()),
                new AnnotationReferenceProvider());
    }
}
