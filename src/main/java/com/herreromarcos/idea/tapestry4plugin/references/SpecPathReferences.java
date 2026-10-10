package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryPaths;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Datei-Referenzen für {@code specification-path} und {@code asset path}:
 * relativ zur Spezifikation, absolut ("/…") gegen Web-Root und Classpath, oder mit Präfix
 * {@code context:} (Web-Root) bzw. {@code classpath:}. URLs (z.B. {@code http:}) bleiben unberührt.
 */
class SpecPathReferences {
    private static final String CONTEXT = "context:";
    private static final String CLASSPATH = "classpath:";

    /** Unbekannte Präfixe sind das Schema einer URL ("http:", "https:" …) und werden unverändert durchgereicht. */
    private static final Pattern URL_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");

    private SpecPathReferences() {
    }

    static PsiReference @NotNull [] create(@NotNull PsiElement value, @NotNull String text, int offset, boolean soft) {
        return create(value, text, offset, soft, null);
    }

    /** @param base Datei, relativ zu der aufgelöst wird (Spezifikation/Template); {@code null} = die enthaltende Datei */
    static PsiReference @NotNull [] create(@NotNull PsiElement value, @NotNull String text, int offset, boolean soft,
                                           @Nullable VirtualFile base) {
        final String mode = Stream.of(CONTEXT, CLASSPATH).filter(text::startsWith).findFirst().orElse(null);
        final String path = mode != null ? text.substring(mode.length()) : text;
        if (mode == null && URL_SCHEME.matcher(path).find() || path.contains("${")) return PsiReference.EMPTY_ARRAY;
        return new PathReferenceSet(path, value, offset + text.length() - path.length(), mode, soft, base).getAllReferences();
    }

    private static class PathReferenceSet extends FileReferenceSet {
        private final @Nullable String mode;
        private final boolean soft;
        private final @Nullable VirtualFile base;

        PathReferenceSet(String text, PsiElement element, int offset, @Nullable String mode,
                         boolean soft, @Nullable VirtualFile base) {
            super(text, element, offset, null, true);
            this.mode = mode;
            this.soft = soft;
            this.base = base;
        }

        @Override
        protected boolean isSoft() {
            return soft;
        }

        @Override
        public @NotNull Collection<PsiFileSystemItem> computeDefaultContexts() {
            final PsiFile file = getContainingFile();
            final VirtualFile vf = base != null ? base : file != null ? file.getOriginalFile().getVirtualFile() : null;
            if (vf == null || file == null) return List.of();
            final List<PsiFileSystemItem> result = new ArrayList<>();
            final PsiManager manager = file.getManager();
            for (final VirtualFile dir : contextDirectories(file, vf)) {
                final PsiDirectory psiDir = dir.isValid() ? manager.findDirectory(dir) : null;
                if (psiDir != null) result.add(psiDir);
            }
            return result;
        }

        /**
         * Basisverzeichnisse laut Spezifikations-Doku ({@code <asset>}):
         * {@code context:} immer ab Web-Root; {@code classpath:} relativ zur Spezifikation, absolut ab Classpath;
         * ohne Präfix relativ zur Spezifikation – bei Spezifikationen in der Web-Anwendung zusätzlich ab Web-Root.
         */
        private List<VirtualFile> contextDirectories(PsiFile file, VirtualFile vf) {
            final List<VirtualFile> dirs = new ArrayList<>();
            final VirtualFile webRoot = TapestryPaths.webRoot(vf);
            final boolean absolute = isAbsolutePathReference();
            if (CONTEXT.equals(mode)) {
                if (webRoot != null) dirs.add(webRoot);
            } else if (absolute) {
                if (mode == null && webRoot != null) dirs.add(webRoot);
                dirs.addAll(TapestryPaths.classpathRoots(file.getProject()));
            } else {
                if (vf.getParent() != null) dirs.add(vf.getParent());
                if (mode == null && webRoot != null) dirs.add(webRoot);
            }
            return dirs;
        }
    }
}
