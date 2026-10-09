package com.herreromarcos.idea.tapestry4plugin.html;

import com.intellij.openapi.project.DumbService;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlAttributeDescriptorsProvider;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.JWCID;

/**
 * Macht {@code jwcid} in HTML bekannt (keine "Unknown attribute"-Warnung) und bietet an Tags mit jwcid
 * die formalen Parameter der Komponente als Attribute an – inkl. Navigation zur {@code <parameter>}-Deklaration.
 */
public class TapestryAttributeDescriptorsProvider implements XmlAttributeDescriptorsProvider {

    @Override
    public XmlAttributeDescriptor @NotNull [] getAttributeDescriptors(final XmlTag tag) {
        if (!isInTemplate(tag)) return XmlAttributeDescriptor.EMPTY;
        final List<XmlAttributeDescriptor> result = new ArrayList<>();
        result.add(new TapestryAttributeDescriptor(JWCID, null));
        final XmlFile spec = componentSpec(tag);
        if (spec != null) {
            for (final ComponentParameter p : ComponentModel.getParameters(spec)) {
                result.add(new TapestryAttributeDescriptor(p.name(), p.declaration()));
            }
        }
        return result.toArray(XmlAttributeDescriptor.EMPTY);
    }

    @Override
    public @Nullable XmlAttributeDescriptor getAttributeDescriptor(final String attributeName, final XmlTag tag) {
        if (attributeName == null || !isInTemplate(tag)) return null;
        if (JWCID.equalsIgnoreCase(attributeName)) return new TapestryAttributeDescriptor(JWCID, null);
        final XmlFile spec = componentSpec(tag);
        final ComponentParameter p = spec != null ? ComponentModel.findParameter(spec, attributeName) : null;
        return p != null ? new TapestryAttributeDescriptor(attributeName, p.declaration()) : null;
    }

    /** Während der Indexierung keine Index-Zugriffe – dann ist nur jwcid bekannt. */
    private static @Nullable XmlFile componentSpec(final XmlTag tag) {
        return DumbService.isDumb(tag.getProject()) ? null : ComponentModel.getComponentSpecOfTag(tag);
    }

    private static boolean isInTemplate(@Nullable final XmlTag tag) {
        return tag != null && TapestryFiles.isTemplateFile(tag.getContainingFile());
    }
}
