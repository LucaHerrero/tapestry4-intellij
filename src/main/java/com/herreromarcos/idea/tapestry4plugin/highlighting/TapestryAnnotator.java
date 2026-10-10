package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.references.TapestryReference;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

/**
 * Prüft Templates, Spezifikationen und Annotationen:
 * <ul>
 *   <li>unaufgelöste Tapestry-Referenzen (Komponenten-ids/-typen, Listener, OGNL, Message-Keys, Assets, Beans)</li>
 *   <li>Template-Regeln ({@link TemplateChecks}) und Spezifikations-Regeln ({@link SpecificationChecks})</li>
 *   <li>hebt Binding-Präfixe ({@code ognl:}, {@code listener:} …) farblich hervor</li>
 * </ul>
 */
public class TapestryAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element instanceof final XmlTag tag) {
            if (TapestryFiles.isTemplateFile(tag.getContainingFile())) TemplateChecks.checkTag(tag, holder);
            return;
        }
        if (element instanceof PsiLiteralExpression && element.getContainingFile() instanceof PsiJavaFile) {
            reportUnresolvedReferences(element, holder);
            return;
        }
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) return;

        final PsiFile file = element.getContainingFile();
        final boolean template = TapestryFiles.isTemplateFile(file);
        if (!template && !TapestryFiles.isSpecFile(file)) return;
        final boolean isJwcid = TapestryConfiguration.isJwcidAttribute(attribute);
        // im Template nur jwcid und die Attribute von Komponenten-Tags
        if (template && !isJwcid && (attribute.getParent() == null || !TapestryConfiguration.isComponentTag(attribute.getParent()))) return;

        highlightBindingPrefix(value, holder);
        if (template && isJwcid) TemplateChecks.checkJwcid(value, holder);
        if (!template) SpecificationChecks.checkAttribute(value, attribute, holder);
        reportUnresolvedReferences(value, holder);
    }

    private static void reportUnresolvedReferences(PsiElement value, AnnotationHolder holder) {
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

    private static boolean isResolved(PsiReference reference) {
        if (reference instanceof final PsiPolyVariantReference poly) return poly.multiResolve(false).length > 0;
        return reference.resolve() != null;
    }

    private static void highlightBindingPrefix(XmlAttributeValue value, AnnotationHolder holder) {
        final BindingExpression binding = BindingExpression.parse(ElementManipulators.getValueText(value), value);
        if (binding.registeredPrefix() == null) return;
        final int start = value.getTextRange().getStartOffset() + ElementManipulators.getValueTextRange(value).getStartOffset();
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange.from(start, binding.expressionStart()))
                .textAttributes(DefaultLanguageHighlighterColors.METADATA)
                .create();
    }
}
