package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import org.jetbrains.annotations.Nullable;

/** Art einer Tapestry-Spezifikation mit Wurzel-Tag und Dateiendung. */
public enum SpecKind {
    PAGE(TapestryConstants.ROOT_PAGE, TapestryConstants.EXT_PAGE),
    COMPONENT(TapestryConstants.ROOT_COMPONENT, TapestryConstants.EXT_COMPONENT),
    APPLICATION(TapestryConstants.ROOT_APPLICATION, TapestryConstants.EXT_APPLICATION),
    LIBRARY(TapestryConstants.ROOT_LIBRARY, TapestryConstants.EXT_LIBRARY);

    private final String rootTag;
    private final String extension;

    SpecKind(String rootTag, String extension) {
        this.rootTag = rootTag;
        this.extension = extension;
    }

    public String getRootTag() {
        return rootTag;
    }

    public static @Nullable SpecKind byRootTag(@Nullable String name) {
        for (final SpecKind kind : values()) {
            if (kind.rootTag.equals(name)) return kind;
        }
        return null;
    }

    public static @Nullable SpecKind byExtension(@Nullable String ext) {
        for (final SpecKind kind : values()) {
            if (kind.extension.equalsIgnoreCase(ext)) return kind;
        }
        return null;
    }
}
