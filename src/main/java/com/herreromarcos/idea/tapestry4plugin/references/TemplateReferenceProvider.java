package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.Jwcid;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import org.jetbrains.annotations.NotNull;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.JWCID;
import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.TAG_PAGE;

/**
 * Referenzen in HTML-Templates:
 * <ul>
 *   <li>{@code jwcid="foo"} → deklarierte Komponente, {@code jwcid="id@Type"} → Komponententyp</li>
 *   <li>übrige Attribute eines jwcid-Tags → Binding-Ausdrücke ({@code ognl:}, {@code listener:} …)</li>
 *   <li>{@code page="Home"} an PageLink → Seite</li>
 * </ul>
 */
class TemplateReferenceProvider extends PsiReferenceProvider {

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) {
            return PsiReference.EMPTY_ARRAY;
        }
        final XmlTag tag = attribute.getParent();
        if (tag == null || !TapestryFiles.isTemplateFile(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;

        final String text = ElementManipulators.getValueText(value);
        final int offset = ElementManipulators.getValueTextRange(value).getStartOffset();
        if (JWCID.equalsIgnoreCase(attribute.getName())) return jwcidReferences(value, text, offset);
        if (tag.getAttribute(JWCID) == null) return PsiReference.EMPTY_ARRAY;

        final BindingExpression binding = BindingExpression.parse(text);
        if (TAG_PAGE.equalsIgnoreCase(attribute.getName()) && binding.isLiteral() && !DumbService.isDumb(element.getProject())
                && TapestryRegistry.isPageLink(ComponentModel.getComponentTypeOfTag(tag))) {
            final TextRange range = TextRange.create(offset + binding.expressionStart(), offset + text.length());
            return new PsiReference[]{new PageReference(value, range)};
        }
        return BindingReferences.create(value, text, offset, null);
    }

    private static PsiReference[] jwcidReferences(final PsiElement value, final String text, final int offset) {
        final Jwcid jwcid = Jwcid.parse(text);
        if (jwcid.special()) return PsiReference.EMPTY_ARRAY;
        if (jwcid.isImplicit()) {
            // "id@Type" deklariert die id selbst – nur der Typ ist eine Referenz
            return new PsiReference[]{new ComponentTypeReference(value, jwcid.typeRange().shiftRight(offset))};
        }
        return new PsiReference[]{new ComponentIdReference(value, jwcid.idRange().shiftRight(offset), true)};
    }
}
