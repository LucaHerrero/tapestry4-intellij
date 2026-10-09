package com.herreromarcos.idea.tapestry4plugin.navigation;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.SpecKind;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

/** Eigene Icons für .page/.jwc/.application/.library im Projektbaum. */
public class TapestryIconProvider extends IconProvider {

    @Override
    public @Nullable Icon getIcon(@NotNull final PsiElement element, final int flags) {
        if (!(element instanceof final XmlFile file) || file.getVirtualFile() == null) return null;
        final SpecKind kind = SpecKind.byExtension(file.getVirtualFile().getExtension());
        if (kind == null || TapestryFiles.getSpecKind(file) != kind) return null;
        return switch (kind) {
            case PAGE -> TapestryIcons.PAGE;
            case COMPONENT -> TapestryIcons.COMPONENT;
            case APPLICATION, LIBRARY -> TapestryIcons.APPLICATION;
        };
    }
}
