package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Seitenname, z.B. {@code page="Home"} an PageLink oder {@code <inject type="page" object="Home"/>}. */
public class PageReference extends TapestryReferenceBase {

    public PageReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return TapestryRegistry.resolvePage(getValue(), getElement());
    }

    @Override
    public Object @NotNull [] getVariants() {
        return TapestryRegistry.getPages(getElement().getProject()).keySet().stream()
                .map(name -> LookupElementBuilder.create(name).withIcon(TapestryIcons.PAGE))
                .toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        // Seiten aus Bibliotheken ("contrib:Foo") werden nicht geprüft
        return getValue().contains(":") ? null : HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Unknown page '%s'".formatted(getValue().trim());
    }
}
