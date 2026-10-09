package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.SpecXml;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * {@code asset:foo} / {@code bean:foo} → Asset bzw. Bean der eigenen Seite/Komponente: {@code <asset name>} /
 * {@code <bean name>} der Spezifikation (inkl. 3.0-Elemente wie {@code <context-asset>}) oder ein Getter mit
 * {@code @Asset} / {@code @Bean} (Name = Eigenschaftsname).
 */
public class SpecChildReference extends TapestryReferenceBase {

    public enum Kind {
        ASSET("asset", ASSET_TAGS, ANNOTATION_ASSET),
        BEAN("bean", List.of(TAG_BEAN), ANNOTATION_BEAN);

        private final String label;
        private final List<String> tags;
        private final String annotation;

        Kind(final String label, final List<String> tags, final String annotation) {
            this.label = label;
            this.tags = tags;
            this.annotation = annotation;
        }
    }

    private final Kind kind;

    public SpecChildReference(@NotNull final PsiElement element, @NotNull final TextRange range, @NotNull final Kind kind) {
        super(element, range);
        this.kind = kind;
    }

    /** Alle Definitionen: Spezifikation zuerst, dann annotierte Getter der Klasse. */
    private Map<String, PsiElement> definitions() {
        final TapestryContext ctx = context();
        final Map<String, PsiElement> result = new LinkedHashMap<>();
        for (final String tag : kind.tags) {
            for (final String name : SpecXml.childValues(ctx.spec(), tag, ATTR_NAME)) {
                result.putIfAbsent(name, SpecXml.findChild(ctx.spec(), tag, ATTR_NAME, name));
            }
        }
        final Map<String, PsiMethod> annotated = ComponentModel.getAnnotatedProperties(ctx.declaredClass(), kind.annotation);
        annotated.forEach(result::putIfAbsent);
        return result;
    }

    @Override
    public @Nullable PsiElement resolve() {
        return definitions().get(getValue().trim());
    }

    @Override
    public Object @NotNull [] getVariants() {
        return definitions().keySet().stream()
                .map(name -> LookupElementBuilder.create(name).withIcon(TapestryIcons.TAPESTRY).withTypeText(kind.label, true))
                .toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        final TapestryContext ctx = context();
        return ctx.isKnown() ? HighlightSeverity.WARNING : null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "No %s '%s' defined in %s".formatted(kind.label, getValue().trim(), context().displayName());
    }
}
