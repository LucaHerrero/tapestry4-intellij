package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import org.jetbrains.annotations.NotNull;

abstract class TapestryReferenceBase extends PsiReferenceBase<PsiElement> implements TapestryReference {

    TapestryReferenceBase(@NotNull final PsiElement element, @NotNull final TextRange range) {
        super(element, range, true);
    }

    protected TapestryContext context() {
        return TapestryModel.getContext(getElement().getContainingFile());
    }
}
