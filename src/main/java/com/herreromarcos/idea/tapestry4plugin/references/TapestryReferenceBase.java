package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class TapestryReferenceBase extends PsiReferenceBase<PsiElement> implements TapestryReference {

    TapestryReferenceBase(@NotNull final PsiElement element, @NotNull final TextRange range) {
        super(element, range, true);
    }

    /** Kontext des Templates/der Spezifikation – bzw. bei Annotationen der umgebenden Seiten-/Komponentenklasse. */
    protected TapestryContext context() {
        if (getElement().getContainingFile() instanceof PsiJavaFile) {
            final PsiClass psiClass = PsiTreeUtil.getParentOfType(getElement(), PsiClass.class);
            if (psiClass != null) return TapestryModel.getContext(psiClass);
        }
        return TapestryModel.getContext(getElement().getContainingFile());
    }

    /** Eine einzelne Referenz als Array für die Provider; {@code null} ergibt ein leeres Array. */
    static PsiReference @NotNull [] single(@Nullable final PsiReference reference) {
        return reference != null ? new PsiReference[]{reference} : PsiReference.EMPTY_ARRAY;
    }
}
