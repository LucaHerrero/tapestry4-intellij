package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;

import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Prüfungen in Spezifikationen: id- und Namens-Syntax, {@code <component>} mit Typ, veraltete Komponenten und
 * Parameter, Aliase, reservierte Namen.
 */
class SpecificationChecks {
    /** Elemente, deren name-Attribut laut Spezifikations-Doku ein Java-Bezeichner sein muss. */
    private static final Set<String> IDENTIFIER_NAMED_TAGS = Set.of(TAG_PARAMETER, TAG_BEAN, TAG_ASSET);
    /** Asset-Name, der das Template der Seite/Komponente bestimmt. */
    private static final String TEMPLATE_ASSET = "$template";

    private SpecificationChecks() {
    }

    /** {@code <component>} braucht laut Spezifikations-Doku {@code type} oder {@code copy-of}. */
    static void checkTag(XmlTag tag, AnnotationHolder holder) {
        if (!TAG_COMPONENT.equals(tag.getName()) || tag.getParentTag() == null) return;
        if (tag.getAttribute(ATTR_TYPE) != null || tag.getAttribute(ATTR_COPY_OF) != null) return;
        final PsiElement name = XmlTagUtil.getStartTagNameElement(tag);
        holder.newAnnotation(HighlightSeverity.ERROR, "Component needs either a type or a copy-of attribute")
                .range(name != null ? name : tag).create();
    }

    static void checkAttribute(XmlAttributeValue value, XmlAttribute attribute, AnnotationHolder holder) {
        final XmlTag tag = attribute.getParent();
        if (tag == null) return;
        final String text = value.getValue().trim();
        if (TAG_COMPONENT.equals(tag.getName()) && ATTR_ID.equals(attribute.getName()) && !text.isEmpty()) {
            ComponentUsageChecks.checkComponentId(holder, text);
        }
        if (IDENTIFIER_NAMED_TAGS.contains(tag.getName()) && ATTR_NAME.equals(attribute.getName()) && !text.isEmpty()
                && !(TAG_ASSET.equals(tag.getName()) && TEMPLATE_ASSET.equals(text))
                && !ComponentUsageChecks.IDENTIFIER.matcher(text).matches()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Invalid %s name '%s': must be a Java identifier".formatted(tag.getName(), text)).create();
        }
        if (TAG_COMPONENT.equals(tag.getName()) && ATTR_TYPE.equals(attribute.getName())) {
            final XmlFile spec = TapestryRegistry.resolveComponentType(text, value);
            if (spec != null) ComponentUsageChecks.checkDeprecatedComponent(holder, spec, text, value);
        }
        final XmlTag component = tag.getParentTag();
        if (BINDING_TAGS.contains(tag.getName()) && ATTR_NAME.equals(attribute.getName()) && component != null
                && TAG_COMPONENT.equals(component.getName())) {
            final String type = ComponentModel.getComponentTypeOfSpecTag(component);
            final XmlFile spec = type != null ? TapestryRegistry.resolveComponentType(type, value) : null;
            if (spec != null) ComponentUsageChecks.checkParameterUsage(holder, spec, type, text, false, Set.of(), value);
        }
    }
}
