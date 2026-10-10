package com.herreromarcos.idea.tapestry4plugin.html;

import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptorsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Macht {@code jwcid} in HTML bekannt (keine "Unknown attribute"-Warnung) und bietet an Tags mit jwcid
 * die formalen Parameter der Komponente als Attribute an – inkl. Navigation zur {@code <parameter>}-Deklaration.
 */
public class TapestryAttributeDescriptorsProvider implements XmlAttributeDescriptorsProvider {

    @Override
    public XmlAttributeDescriptor @NotNull [] getAttributeDescriptors(XmlTag tag) {
        if (isNotInTemplate(tag)) return XmlAttributeDescriptor.EMPTY;
        final List<XmlAttributeDescriptor> result = new ArrayList<>();
        result.add(new TapestryAttributeDescriptor(TapestryConfiguration.getJwcidAttribute(tag.getContainingFile()), null));
        if (TapestryConfiguration.isLocalizationSpan(tag)) {
            result.add(new TapestryAttributeDescriptor(ATTR_KEY, null));
            result.add(new TapestryAttributeDescriptor(ATTR_RAW, null));
        }
        final XmlFile spec = componentSpec(tag);
        if (spec != null) {
            for (final ComponentParameter p : ComponentModel.getParameters(spec)) {
                result.add(new TapestryAttributeDescriptor(p.name(), p.declaration()));
            }
        }
        return result.toArray(XmlAttributeDescriptor.EMPTY);
    }

    @Override
    public @Nullable XmlAttributeDescriptor getAttributeDescriptor(String attributeName, XmlTag tag) {
        if (attributeName == null || isNotInTemplate(tag)) return null;
        final String jwcid = TapestryConfiguration.getJwcidAttribute(tag.getContainingFile());
        if (jwcid.equalsIgnoreCase(attributeName)) return new TapestryAttributeDescriptor(jwcid, null);
        if (TapestryConfiguration.isLocalizationSpan(tag) && (ATTR_KEY.equalsIgnoreCase(attributeName) || ATTR_RAW.equalsIgnoreCase(attributeName))) {
            return new TapestryAttributeDescriptor(attributeName, null);
        }
        final XmlFile spec = componentSpec(tag);
        final ComponentParameter p = spec != null ? ComponentModel.findParameter(spec, attributeName) : null;
        return p != null ? new TapestryAttributeDescriptor(attributeName, p.declaration()) : null;
    }

    /** Während der Indexierung keine Index-Zugriffe – dann ist nur jwcid bekannt. */
    private static @Nullable XmlFile componentSpec(XmlTag tag) {
        return DumbService.isDumb(tag.getProject()) ? null : ComponentModel.getComponentSpecOfTag(tag);
    }

    private static boolean isNotInTemplate(@Nullable XmlTag tag) {
        return tag == null || !TapestryFiles.isTemplateFile(tag.getContainingFile());
    }
}
