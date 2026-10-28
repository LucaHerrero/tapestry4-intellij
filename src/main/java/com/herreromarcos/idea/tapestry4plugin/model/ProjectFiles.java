package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.util.CachedValue;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Dateien des Projektinhalts (ohne Bibliotheken) nach Endung, einmal je Änderung ermittelt ({@link ProjectCache}).
 * {@code FilenameIndex.getAllFilesByExt} durchsucht bei jedem Aufruf alle Dateinamen des VFS – auch die von JDK,
 * Bibliotheken und anderen Projekten – und war im Leistungstest ({@code LargeProjectPerformanceTest}) der größte
 * Kostenfaktor beim Neuaufbau der projektweiten Verzeichnisse.
 */
public class ProjectFiles {
    private static final Key<CachedValue<Map<String, List<VirtualFile>>>> BY_EXTENSION_KEY = Key.create("tapestry4.filesByExtension");

    private ProjectFiles() {
    }

    /** Dateien mit der Endung (ohne Unterscheidung von Groß-/Kleinschreibung) im Projektinhalt. */
    public static @NotNull List<VirtualFile> byExtension(@NotNull Project project, @NotNull String extension) {
        return ProjectCache.get(project, BY_EXTENSION_KEY, () -> compute(project))
                .getOrDefault(extension.toLowerCase(Locale.ROOT), List.of());
    }

    private static Map<String, List<VirtualFile>> compute(Project project) {
        final Map<String, List<VirtualFile>> result = new HashMap<>();
        ProjectFileIndex.getInstance(project).iterateContent(file -> {
            final String extension = file.isDirectory() ? null : file.getExtension();
            if (extension != null) result.computeIfAbsent(extension.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(file);
            return true;
        });
        return result;
    }
}
