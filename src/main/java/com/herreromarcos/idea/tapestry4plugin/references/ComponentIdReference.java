package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.DeclaredComponent;
import com.herreromarcos.idea.tapestry4plugin.model.Jwcid;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Verweis auf eine Komponente der eigenen Seite/Komponente. Welche Komponenten in Frage kommen, hängt vom Ort ab
 * ({@link Scope}).
 */
public class ComponentIdReference extends TapestryReferenceBase {

    public enum Scope {
        /** {@code jwcid="foo"} im Template: nur in Spezifikation oder Klasse deklarierte; Completion bietet auch Typen an. */
        JWCID,
        /** {@code copy-of="foo"}, {@code @Component(copyOf)}: eine zuvor deklarierte Komponente. */
        COPY_OF,
        /**
         * {@code component:foo}, {@code clientId:foo}, {@code @InjectComponent("foo")}, {@code @EventListener(targets)}:
         * jede eingebettete Komponente, auch die im Template mit {@code jwcid="foo@Type"} deklarierten.
         */
        NESTED
    }

    private final Scope scope;

    public ComponentIdReference(@NotNull PsiElement element, @NotNull TextRange range, @NotNull Scope scope) {
        super(element, range);
        this.scope = scope;
    }

    @Override
    public @Nullable PsiElement resolve() {
        final TapestryContext ctx = context();
        final String id = getValue().trim();
        final DeclaredComponent component = ComponentModel.findDeclaredComponent(ctx, id);
        if (component != null) return component.declaration();
        return scope == Scope.NESTED ? ComponentModel.getTemplateComponents(ctx.template()).get(id) : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final TapestryContext ctx = context();
        final Map<String, LookupElement> result = new LinkedHashMap<>();
        for (final DeclaredComponent c : ComponentModel.getDeclaredComponents(ctx)) {
            final String type = ComponentModel.getEffectiveType(ctx, c);
            result.putIfAbsent(c.id(), LookupElementBuilder.create(c.id()).withIcon(TapestryIcons.COMPONENT)
                    .withTypeText(type != null ? type : "", true).bold());
        }
        if (scope == Scope.NESTED) {
            ComponentModel.getTemplateComponents(ctx.template()).forEach((id, value) ->
                    result.putIfAbsent(id, LookupElementBuilder.create(id).withIcon(TapestryIcons.COMPONENT)
                            .withTypeText(StringUtil.notNullize(Jwcid.parse(value.getValue()).type()), true)));
        }
        if (scope == Scope.JWCID) {
            for (final String type : TapestryRegistry.getAllTypeNames(getElement().getProject())) {
                result.putIfAbsent("@" + type, LookupElementBuilder.create("@" + type).withIcon(TapestryIcons.TAPESTRY)
                        .withTypeText("implicit component", true));
            }
            result.put(TapestryConstants.CONTENT_ID, LookupElementBuilder.create(TapestryConstants.CONTENT_ID).withTypeText("template content", true));
            result.put(TapestryConstants.REMOVE_ID, LookupElementBuilder.create(TapestryConstants.REMOVE_ID).withTypeText("removed block", true));
        }
        return result.values().toArray();
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
