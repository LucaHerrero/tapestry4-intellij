package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Kleine Helfer zum Lesen von Tapestry-Spezifikationen (XML). */
public class SpecXml {
    /** Public-Id der Tapestry-3.0-DTD. */
    private static final String DTD_3_0_PUBLIC_ID = "-//Apache Software Foundation//Tapestry Specification 3.0//EN";

    private SpecXml() {
    }

    public static @Nullable XmlTag rootTag(@Nullable PsiFile file) {
        return file instanceof final XmlFile xml ? xml.getRootTag() : null;
    }

    static @Nullable XmlTag rootTag(@NotNull Project project, @NotNull VirtualFile file) {
        return file.isValid() ? rootTag(PsiManager.getInstance(project).findFile(file)) : null;
    }

    /**
     * Verwendet die Spezifikation die Tapestry-3.0-DTD (erkannt an der Public-Id wie in Tapestrys
     * {@code SpecificationParser})? Dort ist {@code <property>} das spätere {@code <meta>}.
     */
    public static boolean isDtd30(@Nullable PsiFile file) {
        return DTD_3_0_PUBLIC_ID.equals(publicId(file));
    }

    /** Public-Id aus dem DOCTYPE oder {@code null}. */
    public static @Nullable String publicId(@Nullable PsiFile file) {
        final XmlDocument document = file instanceof final XmlFile xml ? xml.getDocument() : null;
        final XmlProlog prolog = document != null ? document.getProlog() : null;
        final XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;
        return doctype != null ? doctype.getPublicId() : null;
    }

    /** Schlüssel {@code element@attribut} für Regeln, die von Element und Attribut abhängen. */
    public static @NotNull String attributeKey(@NotNull String tagName, @NotNull String attributeName) {
        return "%s@%s".formatted(tagName, attributeName);
    }

    /**
     * Attribut mit dem Namen der Eigenschaft, die ein Element der Seiten-/Komponentenspezifikation in der Klasse
     * erzeugt: {@code <property name>} ({@code <property-specification name>} der 3.0-DTD), bei {@code <parameter>}
     * das {@code property}-Attribut (3.0: {@code property-name}) oder der Name, sonst das {@code property}-Attribut
     * von {@code <inject>}, {@code <bean>}, {@code <asset>} und {@code <component>}. In der 3.0-DTD ist
     * {@code <property>} das spätere {@code <meta>} und erzeugt keine Eigenschaft.
     */
    public static @Nullable XmlAttribute propertyAttribute(@NotNull XmlTag tag, boolean dtd30) {
        return switch (tag.getName()) {
            case TapestryConstants.TAG_PROPERTY -> dtd30 ? null : tag.getAttribute(TapestryConstants.ATTR_NAME);
            case TapestryConstants.TAG_PROPERTY_SPECIFICATION -> tag.getAttribute(TapestryConstants.ATTR_NAME);
            case TapestryConstants.TAG_PARAMETER -> parameterProperty(tag);
            case TapestryConstants.TAG_INJECT, TapestryConstants.TAG_BEAN, TapestryConstants.TAG_ASSET,
                 TapestryConstants.TAG_COMPONENT -> tag.getAttribute(TapestryConstants.ATTR_PROPERTY);
            default -> null;
        };
    }

    private static @Nullable XmlAttribute parameterProperty(XmlTag tag) {
        for (final String name : List.of(TapestryConstants.ATTR_PROPERTY, TapestryConstants.ATTR_PROPERTY_NAME, TapestryConstants.ATTR_NAME)) {
            final XmlAttribute attribute = tag.getAttribute(name);
            if (attribute != null && !StringUtil.isEmptyOrSpaces(attribute.getValue())) return attribute;
        }
        return null;
    }

    /** Getrimmter Attributwert oder {@code null}, wenn das Attribut fehlt oder leer ist. */
    public static @Nullable String attr(@NotNull XmlTag tag, @NotNull String name) {
        final String value = tag.getAttributeValue(name);
        return StringUtil.isEmptyOrSpaces(value) ? null : value.trim();
    }

    /** Boolesches Attribut: true, yes, on, 1, t, y, aye (laut Spezifikations-Doku). */
    public static boolean isTrue(@Nullable String flag) {
        return flag != null && TapestryConstants.TRUE_VALUES.contains(flag.trim().toLowerCase(Locale.ROOT));
    }

    /** Boolesches Attribut: false, no, off, 0, f, n, nay (laut Spezifikations-Doku). */
    public static boolean isFalse(@Nullable String flag) {
        return flag != null && TapestryConstants.FALSE_VALUES.contains(flag.trim().toLowerCase(Locale.ROOT));
    }

    /** Wert eines {@code <meta key="...">} direkt unter dem Tag (Attribut value oder Textinhalt). */
    public static @Nullable String meta(@Nullable XmlTag parent, @NotNull String key) {
        if (parent == null) return null;
        for (final XmlTag meta : parent.findSubTags(TapestryConstants.TAG_META)) {
            if (key.equals(attr(meta, TapestryConstants.ATTR_KEY))) {
                final String value = meta.getAttributeValue(TapestryConstants.ATTR_VALUE);
                return value != null ? value.trim() : StringUtil.nullize(meta.getValue().getTrimmedText());
            }
        }
        return null;
    }

    /** Kommaseparierte Liste, z.B. aliases oder *-class-packages. */
    public static @NotNull List<String> splitList(@Nullable String value) {
        return Arrays.stream(StringUtils.split(StringUtils.defaultString(value), ','))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
    }

    /**
     * Die {@code <tagName>}-Kinder des Wurzel-Tags nach dem getrimmten Wert von {@code attrName} → dessen
     * Wert-Element; bei gleichen Werten gewinnt das erste Vorkommen.
     */
    public static void collectNamedChildren(@Nullable XmlFile spec, @NotNull String tagName, @NotNull String attrName,
                                            @NotNull Map<String, ? super XmlAttributeValue> out) {
        final XmlTag root = rootTag(spec);
        if (root == null) return;
        for (final XmlTag tag : root.findSubTags(tagName)) {
            final XmlAttribute attr = tag.getAttribute(attrName);
            final XmlAttributeValue value = attr != null ? attr.getValueElement() : null;
            if (value != null && !value.getValue().isBlank()) out.putIfAbsent(value.getValue().trim(), value);
        }
    }
}
