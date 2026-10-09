package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.openapi.util.IconLoader;

import javax.swing.Icon;

public class TapestryIcons {
    public static final Icon TAPESTRY = IconLoader.getIcon("/icons/tapestry.svg", TapestryIcons.class);
    public static final Icon PAGE = IconLoader.getIcon("/icons/page.svg", TapestryIcons.class);
    public static final Icon COMPONENT = IconLoader.getIcon("/icons/component.svg", TapestryIcons.class);
    public static final Icon APPLICATION = IconLoader.getIcon("/icons/application.svg", TapestryIcons.class);

    private TapestryIcons() {
    }
}
