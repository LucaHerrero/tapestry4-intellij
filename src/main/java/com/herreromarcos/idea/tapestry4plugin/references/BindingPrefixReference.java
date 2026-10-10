package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.model.BindingPrefixes;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Das Präfix eines Binding-Ausdrucks ({@code ognl}, {@code listener}, projekteigene …) → seine Registrierung
 * {@code <binding prefix>} in tapestry.bindings.BindingFactories. Ein nicht registriertes Präfix gehört laut
 * BindingSourceImpl zum Ausdruck; ist das Standard-Präfix OGNL, ergibt das einen ungültigen OGNL-Ausdruck.
 */
public class BindingPrefixReference extends TapestryReferenceBase {

    public BindingPrefixReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return BindingPrefixes.getDeclarations(getElement().getProject()).get(getValue());
    }

    @Override
    public Object @NotNull [] getVariants() {
        // die Präfix-Completion liefert TapestryCompletionContributor
        return EMPTY_ARRAY;
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        // dokumentierte Präfixe sind auch ohne Tapestry-JAR im Classpath gültig
        if (TapestryConstants.BINDING_PREFIXES.contains(getValue())) return null;
        return BindingPrefixes.isRegistryKnown(getElement().getProject()) ? HighlightSeverity.WARNING : null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Unknown binding prefix '%s': Tapestry evaluates the whole value as OGNL expression".formatted(getValue());
    }
}
