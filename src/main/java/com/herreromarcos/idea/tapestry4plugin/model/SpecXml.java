package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Kleine Helfer zum Lesen von Tapestry-Spezifikationen (XML). */
public class SpecXml {
    private SpecXml() {
    }

    public static @Nullable XmlTag rootTag(@Nullable final PsiFile file) {
        return file instanceof final XmlFile xml ? xml.getRootTag() : null;
    }

    static @Nullable XmlTag rootTag(@NotNull final Project project, @NotNull final VirtualFile file) {
        return file.isValid() ? rootTag(PsiManager.getInstance(project).findFile(file)) : null;
    }

    /** Getrimmter Attributwert oder {@code null}, wenn das Attribut fehlt oder leer ist. */
    public static @Nullable String attr(@NotNull final XmlTag tag, @NotNull final String name) {
        final String value = tag.getAttributeValue(name);
        return StringUtil.isEmptyOrSpaces(value) ? null : value.trim();
    }

    /** Tapestry-Flags: "yes"/"true". */
    public static boolean isTrue(@Nullable final String flag) {
        return "yes".equalsIgnoreCase(flag) || "true".equalsIgnoreCase(flag);
    }

    /** Tapestry-Flags: "no"/"false". */
    public static boolean isFalse(@Nullable final String flag) {
        return "no".equalsIgnoreCase(flag) || "false".equalsIgnoreCase(flag);
    }

    /** Kommaseparierte Liste, z.B. aliases oder *-class-packages. */
    public static @NotNull List<String> splitList(@Nullable final String value) {
        if (StringUtil.isEmptyOrSpaces(value)) return List.of();
        final List<String> result = new ArrayList<>();
        for (final String part : value.split(",")) {
            if (!part.isBlank()) result.add(part.trim());
        }
        return result;
    }

    /** Wert-Element von {@code <tagName attrName="value">} unterhalb des Wurzel-Tags. */
    public static @Nullable XmlAttributeValue findChild(@Nullable final XmlFile spec, @NotNull final String tagName,
                                                        @NotNull final String attrName, @NotNull final String value) {
        final XmlTag root = rootTag(spec);
        if (root == null) return null;
        for (final XmlTag tag : root.findSubTags(tagName)) {
            final XmlAttribute attr = tag.getAttribute(attrName);
            if (attr != null && value.equals(StringUtil.trim(attr.getValue()))) return attr.getValueElement();
        }
        return null;
    }

    /** Alle Werte von {@code attrName} der {@code <tagName>}-Kinder des Wurzel-Tags. */
    public static @NotNull List<String> childValues(@Nullable final XmlFile spec, @NotNull final String tagName, @NotNull final String attrName) {
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
