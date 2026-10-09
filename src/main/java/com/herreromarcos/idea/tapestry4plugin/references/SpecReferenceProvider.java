package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
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
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;
import static com.herreromarcos.idea.tapestry4plugin.references.TapestryReferenceBase.single;

/**
 * Referenzen in Spezifikationen (.page/.jwc/.application/.library), Tapestry-4.0/4.1- und 3.0-DTD.
 * Binding-Referenzen ohne Präfix sind OGNL bzw. das per {@code default-binding-prefix} konfigurierte Präfix.
 */
class SpecReferenceProvider extends PsiReferenceProvider {
    private static final Set<String> CLASS_ATTRIBUTE_TAGS = Set.of(ROOT_PAGE, ROOT_COMPONENT, TAG_EXTENSION);
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

        if (ATTR_CLASS.equals(attrName) && CLASS_ATTRIBUTE_TAGS.contains(tagName)
                || "engine-class".equals(attrName) && ROOT_APPLICATION.equals(tagName)) {
            return classReferences(value, text, offset);
        }
        if (ATTR_SPECIFICATION_PATH.equals(attrName) && SPEC_PATH_TAGS.contains(tagName)) {
            return SpecPathReferences.create(value, text, offset, false);
        }
        return switch ("%s@%s".formatted(tagName, attrName)) {
            case "component@type" -> single(new ComponentTypeReference(value, valueRange));
            case "component@copy-of" -> single(new ComponentIdReference(value, valueRange, false));
            case "binding@name", "inherited-binding@name", "static-binding@name", "message-binding@name",
                 "listener-binding@name" -> single(new ParameterNameReference(value, valueRange));
            case "binding@value" -> bindingValueReferences(value, tag, text, offset);
            case "set@value", "property@initial-value", "parameter@default-value" ->
                    BindingReferences.create(value, text, offset, TapestryConfiguration.getDefaultBindingPrefix(element.getContainingFile()));
            case "bean@class" -> beanClassReferences(value, text, offset);
            case "inject@object" -> injectReferences(value, tag, text, offset, valueRange);
            case "asset@path" -> SpecPathReferences.create(value, text, offset, true);
            // Tapestry-3.0-DTD: Ausdrücke sind immer OGNL, Nachrichten über key
            case "binding@expression", "property-specification@initial-value", "set-property@expression" ->
                    BindingReferences.createOgnl(value, text, offset);
            case "message-binding@key", "set-message-property@key" -> single(new MessageKeyReference(value, valueRange));
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** {@code <binding name="page" value="literal:Home"/>} an PageLink → Seite, sonst Binding-Ausdruck. */
    private static PsiReference[] bindingValueReferences(final XmlAttributeValue value, final XmlTag bindingTag,
                                                         final String text, final int offset) {
        final String defaultPrefix = TapestryConfiguration.getDefaultBindingPrefix(value.getContainingFile());
        final XmlTag component = bindingTag.getParentTag();
        final BindingExpression binding = BindingExpression.parse(text, value);
        if (TAG_PAGE.equals(bindingTag.getAttributeValue(ATTR_NAME)) && PREFIX_LITERAL.equals(binding.effectivePrefix(defaultPrefix))
                && component != null && TapestryRegistry.isPageLink(component.getAttributeValue(ATTR_TYPE))) {
            return single(new PageReference(value, TextRange.create(offset + binding.expressionStart(), offset + text.length())));
        }
        return BindingReferences.create(value, text, offset, defaultPrefix);
    }

    /**
     * {@code <bean class="...">}: nur der Klassenname vor dem ersten Komma ("lightweight initialization",
     * z.B. {@code StringValidator,required,minimumLength=10}); einfache Namen über bean-class-packages.
     */
    private static PsiReference[] beanClassReferences(final XmlAttributeValue value, final String text, final int offset) {
        final String className = StringUtils.substringBefore(text, ",").trim();
        if (className.isEmpty()) return PsiReference.EMPTY_ARRAY;
        final int start = offset + text.indexOf(className);
        if (!className.contains(".")) return single(new BeanClassReference(value, TextRange.from(start, className.length())));
        return classReferences(value, className, start);
    }

    private static PsiReference[] classReferences(final XmlAttributeValue value, final String className, final int offset) {
        final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
        provider.setSoft(false);
        return provider.getReferencesByString(className, value, offset);
    }

    /** {@code <inject type="page">} → Seite, {@code type="script"} → Skriptdatei, {@code type="state|state-flag"} → State Object. */
    private static PsiReference[] injectReferences(final XmlAttributeValue value, final XmlTag tag, final String text,
                                                   final int offset, final TextRange valueRange) {
        final String type = tag.getAttributeValue(ATTR_TYPE);
        if ("page".equals(type)) return single(new PageReference(value, valueRange));
        if ("script".equals(type)) return SpecPathReferences.create(value, text, offset, true);
        if ("state".equals(type) || "state-flag".equals(type)) return single(new StateObjectReference(value, valueRange));
        return PsiReference.EMPTY_ARRAY;
    }
}
