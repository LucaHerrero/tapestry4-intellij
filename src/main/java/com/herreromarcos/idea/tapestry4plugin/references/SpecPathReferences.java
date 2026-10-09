package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryPaths;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Datei-Referenzen für {@code specification-path} und {@code asset path}:
 * relativ zur Spezifikation, absolut ("/…") gegen Web-Root und Classpath, oder mit Präfix
 * {@code context:} (Web-Root) bzw. {@code classpath:}.
 */
class SpecPathReferences {
    private static final String CONTEXT = "context:";
    private static final String CLASSPATH = "classpath:";

    private SpecPathReferences() {
    }

    static PsiReference @NotNull [] create(@NotNull PsiElement value, @NotNull String text, int offset, boolean soft) {
        String mode = null;
        for (final String prefix : List.of(CONTEXT, CLASSPATH)) {
            if (text.startsWith(prefix)) {
                mode = prefix;
                text = text.substring(prefix.length());
                offset += prefix.length();
                break;
            }
        }
        if (text.contains("${")) return PsiReference.EMPTY_ARRAY;
        return new PathReferenceSet(text, value, offset, mode, soft).getAllReferences();
    }

    private static class PathReferenceSet extends FileReferenceSet {
        private final @Nullable String mode;
        private final boolean soft;

        PathReferenceSet(final String text, final PsiElement element, final int offset, @Nullable final String mode, final boolean soft) {
            super(text, element, offset, null, true);
            this.mode = mode;
            this.soft = soft;
        }

        @Override
        protected boolean isSoft() {
            return soft;
        }

        @Override
        public @NotNull Collection<PsiFileSystemItem> computeDefaultContexts() {
            final PsiFile file = getContainingFile();
            final VirtualFile vf = file != null ? file.getOriginalFile().getVirtualFile() : null;
            if (vf == null) return List.of();
            final List<PsiFileSystemItem> result = new ArrayList<>();
            final PsiManager manager = file.getManager();
            for (final VirtualFile dir : contextDirectories(file, vf)) {
                final PsiDirectory psiDir = dir.isValid() ? manager.findDirectory(dir) : null;
                if (psiDir != null) result.add(psiDir);
            }
            return result;
        }

        private List<VirtualFile> contextDirectories(final PsiFile file, final VirtualFile vf) {
            final List<VirtualFile> dirs = new ArrayList<>();
            final VirtualFile webRoot = TapestryPaths.webRoot(vf);
            final boolean context = CONTEXT.equals(mode) || (mode == null && isAbsolutePathReference());
            final boolean classpath = CLASSPATH.equals(mode) || (mode == null && isAbsolutePathReference());
            if (context && webRoot != null) dirs.add(webRoot);
            if (classpath) dirs.addAll(TapestryPaths.classpathRoots(file.getProject()));
            if (!context && !classpath && vf.getParent() != null) dirs.add(vf.getParent());
            return dirs;
        }
    }
}
