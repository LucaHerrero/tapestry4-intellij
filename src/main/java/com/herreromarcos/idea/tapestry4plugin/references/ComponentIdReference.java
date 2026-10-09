package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.DeclaredComponent;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Verweis auf eine deklarierte Komponente: {@code jwcid="foo"}, {@code copy-of="foo"}, {@code component:foo}. */
public class ComponentIdReference extends TapestryReferenceBase {
    private final boolean offerTypes;

    public ComponentIdReference(@NotNull final PsiElement element, @NotNull final TextRange range, final boolean offerTypes) {
        super(element, range);
        this.offerTypes = offerTypes;
    }

    @Override
    public @Nullable PsiElement resolve() {
        final DeclaredComponent component = ComponentModel.findDeclaredComponent(context(), getValue().trim());
        return component != null ? component.declaration() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final TapestryContext ctx = context();
        final List<LookupElement> result = new ArrayList<>();
        for (final DeclaredComponent c : ComponentModel.getDeclaredComponents(ctx)) {
            final String type = ComponentModel.getEffectiveType(ctx, c);
            result.add(LookupElementBuilder.create(c.id()).withIcon(TapestryIcons.COMPONENT)
                    .withTypeText(type != null ? type : "", true).bold());
        }
        if (offerTypes) {
            for (final String type : TapestryRegistry.getAllTypeNames(getElement().getProject())) {
                result.add(LookupElementBuilder.create("@" + type).withIcon(TapestryIcons.TAPESTRY)
                        .withTypeText("implicit component", true));
            }
            result.add(LookupElementBuilder.create(TapestryConstants.CONTENT_ID).withTypeText("template content", true));
            result.add(LookupElementBuilder.create(TapestryConstants.REMOVE_ID).withTypeText("removed block", true));
        }
        return result.toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return context().isKnown() ? HighlightSeverity.ERROR : null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Component '%s' is not declared in %s".formatted(getValue().trim(), context().displayName());
    }
}
