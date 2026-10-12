package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Meta-Schlüssel: {@code meta:page-size}, {@code <inject type="meta" object="page-size">}, {@code @InjectMeta("page-size")}
 * → {@code <meta key>} bzw. {@code @Meta}-Eintrag (User's Guide, "Property Injection" und "Component Bindings").
 * Unaufgelöste Schlüssel werden nicht gemeldet: Der Wert kann auch aus web.xml, HiveMind-Symbolen oder
 * JVM-Properties stammen.
 */
public class MetaKeyReference extends TapestryReferenceBase {

    public MetaKeyReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return TapestryConfiguration.findMetaDeclarations(context()).get(getValue().trim());
    }

    @Override
    public Object @NotNull [] getVariants() {
        return TapestryConfiguration.findMetaDeclarations(context()).keySet().stream()
                .map(key -> LookupElementBuilder.create(key).withIcon(TapestryIcons.TAPESTRY).withTypeText("meta", true))
                .toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Meta key '%s' not found".formatted(getValue().trim());
    }
}
