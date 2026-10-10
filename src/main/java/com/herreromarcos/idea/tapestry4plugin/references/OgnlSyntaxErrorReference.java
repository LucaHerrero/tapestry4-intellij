package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Markiert die Stelle, an der der OGNL-Parser einen Ausdruck ablehnt – Tapestry würde hier zur Laufzeit scheitern. */
public class OgnlSyntaxErrorReference extends TapestryReferenceBase {
    private final String message;

    public OgnlSyntaxErrorReference(@NotNull PsiElement element, @NotNull TextRange range, @NotNull String message) {
        super(element, range);
        this.message = message;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        return EMPTY_ARRAY;
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return message;
    }
}
