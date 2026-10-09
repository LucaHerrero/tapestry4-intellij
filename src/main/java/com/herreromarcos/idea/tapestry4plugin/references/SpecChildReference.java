package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.SpecXml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code asset:foo} / {@code bean:foo} → {@code <asset name="foo">} / {@code <bean name="foo">} der eigenen Spezifikation. */
public class SpecChildReference extends TapestryReferenceBase {
    private final String tagName;
    private final String attrName;

    public SpecChildReference(@NotNull final PsiElement element, @NotNull final TextRange range, final String tagName, final String attrName) {
        super(element, range);
        this.tagName = tagName;
        this.attrName = attrName;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return SpecXml.findChild(context().spec(), tagName, attrName, getValue().trim());
    }

    @Override
    public Object @NotNull [] getVariants() {
        return SpecXml.childValues(context().spec(), tagName, attrName).stream()
                .map(name -> LookupElementBuilder.create(name).withIcon(TapestryIcons.TAPESTRY).withTypeText(tagName, true))
                .toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        // Assets/Beans können auch per Annotation in der Klasse deklariert sein → nur Warnung
        return context().spec() != null ? HighlightSeverity.WARNING : null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "No <%s %s=\"%s\"> in %s".formatted(tagName, attrName, getValue().trim(), context().displayName());
    }
}
