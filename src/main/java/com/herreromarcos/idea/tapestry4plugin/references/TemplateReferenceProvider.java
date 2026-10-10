package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.Jwcid;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
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
import org.jetbrains.annotations.NotNull;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.ATTR_KEY;
import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.TAG_PAGE;
import static com.herreromarcos.idea.tapestry4plugin.references.TapestryReferenceBase.single;

/**
 * Referenzen in HTML-Templates:
 * <ul>
 *   <li>{@code jwcid="foo"} → deklarierte Komponente, {@code jwcid="id@Type"} → Komponententyp</li>
 *   <li>übrige Attribute eines jwcid-Tags → Binding-Ausdrücke ({@code ognl:}, {@code listener:} …)</li>
 *   <li>{@code page="Home"} an PageLink → Seite</li>
 *   <li>{@code <span key="hello">} (Lokalisierungs-Direktive) → Message-Key</li>
 * </ul>
 */
class TemplateReferenceProvider extends PsiReferenceProvider {

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) {
            return PsiReference.EMPTY_ARRAY;
        }
        final XmlTag tag = attribute.getParent();
        if (tag == null || !TapestryFiles.isTemplateFile(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;

        final String text = ElementManipulators.getValueText(value);
        final int offset = ElementManipulators.getValueTextRange(value).getStartOffset();
        if (TapestryConfiguration.isJwcidAttribute(attribute)) return jwcidReferences(value, text, offset);
        if (!TapestryConfiguration.isComponentTag(tag)) {
            // Lokalisierungs-Direktive <span key="hello">: Schlüssel ohne message:-Präfix (User's Guide, "Templates")
            return TapestryConfiguration.isLocalizationSpan(tag) && ATTR_KEY.equalsIgnoreCase(attribute.getName())
                    ? single(new MessageKeyReference(value, ElementManipulators.getValueTextRange(value)))
                    : PsiReference.EMPTY_ARRAY;
        }

        final BindingExpression binding = BindingExpression.parse(text, value);
        if (TAG_PAGE.equalsIgnoreCase(attribute.getName()) && binding.isLiteral() && !DumbService.isDumb(element.getProject())
                && TapestryRegistry.isPageLink(ComponentModel.getComponentTypeOfTag(tag))) {
            final TextRange range = TextRange.create(offset + binding.expressionStart(), offset + text.length());
            return single(new PageReference(value, range));
        }
        return BindingReferences.create(value, text, offset, null);
    }

    private static PsiReference[] jwcidReferences(PsiElement value, String text, int offset) {
        final Jwcid jwcid = Jwcid.parse(text);
        if (jwcid.special()) return PsiReference.EMPTY_ARRAY;
        if (jwcid.isImplicit()) {
            // "id@Type" deklariert die id selbst – nur der Typ ist eine Referenz
            return single(new ComponentTypeReference(value, jwcid.typeRange().shiftRight(offset)));
        }
        return single(new ComponentIdReference(value, jwcid.idRange().shiftRight(offset), true));
    }
}
