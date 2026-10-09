package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.herreromarcos.idea.tapestry4plugin.model.DeclaredComponent;
import com.herreromarcos.idea.tapestry4plugin.model.Jwcid;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.herreromarcos.idea.tapestry4plugin.references.TapestryReference;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.JWCID;

/**
 * Prüft Templates und Spezifikationen:
 * <ul>
 *   <li>unaufgelöste Tapestry-Referenzen (Komponenten-ids/-typen, Listener, OGNL, Message-Keys, Assets, Beans)</li>
 *   <li>leere und doppelte Komponenten-ids im Template</li>
 *   <li>nicht gebundene Pflichtparameter</li>
 *   <li>hebt Binding-Präfixe ({@code ognl:}, {@code listener:} …) farblich hervor</li>
 * </ul>
 */
public class TapestryAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull final PsiElement element, @NotNull final AnnotationHolder holder) {
        if (element instanceof final XmlTag tag) {
            if (TapestryFiles.isTemplateFile(tag.getContainingFile())) checkRequiredParameters(tag, holder);
            return;
        }
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) return;

        final PsiFile file = element.getContainingFile();
        final boolean template = TapestryFiles.isTemplateFile(file);
        if (!template && !TapestryFiles.isSpecFile(file)) return;
        final boolean isJwcid = JWCID.equalsIgnoreCase(attribute.getName());
        // im Template nur jwcid und die Attribute von Komponenten-Tags
        if (template && !isJwcid && (attribute.getParent() == null || attribute.getParent().getAttribute(JWCID) == null)) return;

        highlightBindingPrefix(value, holder);
        if (template && isJwcid) checkJwcid(value, holder);
        reportUnresolvedReferences(value, holder);
    }

    private static void reportUnresolvedReferences(final XmlAttributeValue value, final AnnotationHolder holder) {
        final int start = value.getTextRange().getStartOffset();
        for (final PsiReference reference : value.getReferences()) {
            if (!(reference instanceof final TapestryReference tapestryReference)) continue;
            final TextRange range = reference.getRangeInElement();
            if (range.isEmpty() || isResolved(reference)) continue;
            final HighlightSeverity severity = tapestryReference.getUnresolvedSeverity();
            if (severity == null) continue;
            holder.newAnnotation(severity, tapestryReference.getUnresolvedMessage())
                    .range(range.shiftRight(start))
                    .highlightType(severity == HighlightSeverity.ERROR ? ProblemHighlightType.LIKE_UNKNOWN_SYMBOL
                            : ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
                    .create();
        }
    }

    private static boolean isResolved(final PsiReference reference) {
        if (reference instanceof final PsiPolyVariantReference poly) return poly.multiResolve(false).length > 0;
        return reference.resolve() != null;
    }

    private static void highlightBindingPrefix(final XmlAttributeValue value, final AnnotationHolder holder) {
        final BindingExpression binding = BindingExpression.parse(ElementManipulators.getValueText(value));
        if (binding.prefix() == null) return;
        final int start = value.getTextRange().getStartOffset() + ElementManipulators.getValueTextRange(value).getStartOffset();
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange.from(start, binding.expressionStart()))
                .textAttributes(DefaultLanguageHighlighterColors.METADATA)
                .create();
    }

    private static void checkJwcid(final XmlAttributeValue value, final AnnotationHolder holder) {
        if (value.getValue().isBlank()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Empty jwcid").create();
            return;
        }
        final String id = Jwcid.parse(value.getValue()).id();
        if (id != null && idCounts((XmlFile) value.getContainingFile()).getOrDefault(id, 0) > 1) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate component id '%s' in template".formatted(id)).create();
        }
    }

    /** Wie oft kommt jede Komponenten-id ("foo", "foo@Insert") im Template vor? */
    private static Map<String, Integer> idCounts(final XmlFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            final Map<String, Integer> counts = new HashMap<>();
            for (final XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
                final String value = tag.getAttributeValue(JWCID);
                final String id = value != null ? Jwcid.parse(value).id() : null;
                if (id != null) counts.merge(id, 1, Integer::sum);
            }
            return CachedValueProvider.Result.create(counts, file);
        });
    }

    private static void checkRequiredParameters(final XmlTag tag, final AnnotationHolder holder) {
        final XmlAttribute jwcidAttr = tag.getAttribute(JWCID);
        if (jwcidAttr == null || jwcidAttr.getValueElement() == null) return;
        final XmlFile spec = ComponentModel.getComponentSpecOfTag(tag);
        if (spec == null) return;

        final Set<String> bound = boundParameters(tag, Jwcid.parse(jwcidAttr.getValueElement().getValue()));
        for (final ComponentParameter p : ComponentModel.getParameters(spec)) {
            if (!p.required() || isBound(p, bound)) continue;
            holder.newAnnotation(HighlightSeverity.WARNING, "Required parameter '%s' of component '%s' is not bound"
                            .formatted(p.name(), ComponentModel.getComponentTypeOfTag(tag)))
                    .range(jwcidAttr.getValueElement())
                    .create();
        }
    }

    /** Im Template gebundene Parameter (Attribute) plus – bei deklarierten Komponenten – die Bindings der Spezifikation. */
    private static Set<String> boundParameters(final XmlTag tag, final Jwcid jwcid) {
        final Set<String> bound = new HashSet<>();
        for (final XmlAttribute attribute : tag.getAttributes()) {
            bound.add(ComponentModel.normalize(attribute.getName()));
        }
        if (jwcid.isDeclaredReference() && jwcid.id() != null) {
            final TapestryContext ctx = TapestryModel.getContext(tag.getContainingFile());
            final DeclaredComponent declared = ComponentModel.findDeclaredComponent(ctx, jwcid.id());
            if (declared != null) bound.addAll(ComponentModel.getBoundParameters(ctx, declared));
        }
        return bound;
    }

    private static boolean isBound(final ComponentParameter parameter, final Set<String> bound) {
        return bound.contains(ComponentModel.normalize(parameter.name()))
                || parameter.aliases().stream().anyMatch(alias -> bound.contains(ComponentModel.normalize(alias)));
    }
}
