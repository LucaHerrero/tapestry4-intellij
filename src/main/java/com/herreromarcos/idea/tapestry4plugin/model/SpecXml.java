package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Kleine Helfer zum Lesen von Tapestry-Spezifikationen (XML). */
public class SpecXml {
    private SpecXml() {
    }

    public static @Nullable XmlTag rootTag(@Nullable PsiFile file) {
        return file instanceof final XmlFile xml ? xml.getRootTag() : null;
    }

    static @Nullable XmlTag rootTag(@NotNull Project project, @NotNull VirtualFile file) {
        return file.isValid() ? rootTag(PsiManager.getInstance(project).findFile(file)) : null;
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

    /** Wert-Element von {@code <tagName attrName="value">} unterhalb des Wurzel-Tags. */
    public static @Nullable XmlAttributeValue findChild(@Nullable XmlFile spec, @NotNull String tagName,
                                                        @NotNull String attrName, @NotNull String value) {
        final XmlTag root = rootTag(spec);
        if (root == null) return null;
        for (final XmlTag tag : root.findSubTags(tagName)) {
            final XmlAttribute attr = tag.getAttribute(attrName);
            if (attr != null && value.equals(StringUtil.trim(attr.getValue()))) return attr.getValueElement();
        }
        return null;
    }

    /** Alle Werte von {@code attrName} der {@code <tagName>}-Kinder des Wurzel-Tags. */
    public static @NotNull List<String> childValues(@Nullable XmlFile spec, @NotNull String tagName, @NotNull String attrName) {
        final XmlTag root = rootTag(spec);
        if (root == null) return List.of();
        final List<String> result = new ArrayList<>();
        for (final XmlTag tag : root.findSubTags(tagName)) {
            final String value = attr(tag, attrName);
            if (value != null) result.add(value);
        }
        return result;
    }
}
