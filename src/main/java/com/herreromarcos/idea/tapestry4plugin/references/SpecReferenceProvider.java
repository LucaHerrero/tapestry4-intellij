package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/** Referenzen in Spezifikationen (.page/.jwc/.application/.library). */
class SpecReferenceProvider extends PsiReferenceProvider {
    private static final Set<String> CLASS_ATTRIBUTE_TAGS = Set.of(ROOT_PAGE, ROOT_COMPONENT, ROOT_APPLICATION, TAG_BEAN, TAG_EXTENSION);
    private static final Set<String> SPEC_PATH_TAGS = Set.of(TAG_PAGE, TAG_COMPONENT_TYPE, TAG_LIBRARY);

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) {
            return PsiReference.EMPTY_ARRAY;
        }
        final XmlTag tag = attribute.getParent();
        if (tag == null || !TapestryFiles.isSpecFile(element.getContainingFile())) return PsiReference.EMPTY_ARRAY;

        final String tagName = tag.getName();
        final String attrName = attribute.getName();
        final String text = ElementManipulators.getValueText(value);
        final TextRange valueRange = ElementManipulators.getValueTextRange(value);
        final int offset = valueRange.getStartOffset();

        if (ATTR_CLASS.equals(attrName) && CLASS_ATTRIBUTE_TAGS.contains(tagName)) {
            final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
            provider.setSoft(false);
            return provider.getReferencesByElement(value);
        }
        if (ATTR_SPECIFICATION_PATH.equals(attrName) && SPEC_PATH_TAGS.contains(tagName)) {
            return SpecPathReferences.create(value, text, offset, false);
        }
        return switch ("%s@%s".formatted(tagName, attrName)) {
            case "component@type" -> single(new ComponentTypeReference(value, valueRange));
            case "component@copy-of" -> single(new ComponentIdReference(value, valueRange, false));
            case "binding@name" -> single(new ParameterNameReference(value, valueRange));
            case "binding@value" -> bindingValueReferences(value, tag, text, offset);
            case "set@value", "property@initial-value" -> BindingReferences.create(value, text, offset, PREFIX_OGNL);
            case "parameter@default-value" -> BindingReferences.create(value, text, offset, null);
            case "inject@object" -> "page".equals(tag.getAttributeValue(ATTR_TYPE))
                    ? single(new PageReference(value, valueRange)) : PsiReference.EMPTY_ARRAY;
            case "asset@path" -> SpecPathReferences.create(value, text, offset, true);
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** {@code <binding name="page" value="literal:Home"/>} an PageLink → Seite, sonst Binding-Ausdruck (Standard: OGNL). */
    private static PsiReference[] bindingValueReferences(final XmlAttributeValue value, final XmlTag bindingTag, final String text, final int offset) {
        final XmlTag component = bindingTag.getParentTag();
        final BindingExpression binding = BindingExpression.parse(text);
        if (TAG_PAGE.equals(bindingTag.getAttributeValue(ATTR_NAME)) && PREFIX_LITERAL.equals(binding.prefix())
                && component != null && TapestryRegistry.isPageLink(component.getAttributeValue(ATTR_TYPE))) {
            return single(new PageReference(value, TextRange.create(offset + binding.expressionStart(), offset + text.length())));
        }
        return BindingReferences.create(value, text, offset, PREFIX_OGNL);
    }

    private static PsiReference[] single(final PsiReference reference) {
        return new PsiReference[]{reference};
    }
}
