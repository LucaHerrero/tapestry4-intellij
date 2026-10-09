package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.DeclaredComponent;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/** Prüfungen in Spezifikationen: id-Syntax, veraltete Komponenten und Parameter, Aliase, reservierte Namen. */
class SpecificationChecks {

    private SpecificationChecks() {
    }

    static void checkAttribute(final XmlAttributeValue value, final XmlAttribute attribute, final AnnotationHolder holder) {
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
            final String type = componentType(component);
            final XmlFile spec = type != null ? TapestryRegistry.resolveComponentType(type, value) : null;
            if (spec != null) ComponentUsageChecks.checkParameterUsage(holder, spec, type, text, false, Set.of(), value);
        }
    }

    /** Typ eines {@code <component>}: über die Deklaration (inkl. copy-of), sonst das type-Attribut. */
    private static @Nullable String componentType(final XmlTag component) {
        final TapestryContext ctx = TapestryModel.getContext(component.getContainingFile());
        final String id = component.getAttributeValue(ATTR_ID);
        final DeclaredComponent declared = id != null ? ComponentModel.findDeclaredComponent(ctx, id.trim()) : null;
        return declared != null ? ComponentModel.getEffectiveType(ctx, declared) : component.getAttributeValue(ATTR_TYPE);
    }
}
