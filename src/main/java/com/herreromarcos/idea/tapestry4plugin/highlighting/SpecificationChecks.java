package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/** Prüfungen in Spezifikationen: id-Syntax, veraltete Komponenten und Parameter, Aliase, reservierte Namen. */
class SpecificationChecks {

    private SpecificationChecks() {
    }

    static void checkAttribute(XmlAttributeValue value, XmlAttribute attribute, AnnotationHolder holder) {
        final XmlTag tag = attribute.getParent();
        if (tag == null) return;
        final String text = value.getValue().trim();
        if (TAG_COMPONENT.equals(tag.getName()) && ATTR_ID.equals(attribute.getName()) && !text.isEmpty()) {
            ComponentUsageChecks.checkComponentId(holder, text);
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
