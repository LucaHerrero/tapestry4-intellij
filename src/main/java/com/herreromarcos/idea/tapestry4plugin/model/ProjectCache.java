package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/** Projektweit gecachte Werte, gültig bis zur nächsten PSI- oder Classpath-Änderung. */
class ProjectCache {
    private ProjectCache() {
    }

    static <T> @NotNull T get(@NotNull Project project, @NotNull Key<CachedValue<T>> key, @NotNull Supplier<T> compute) {
        return CachedValuesManager.getManager(project).getCachedValue(project, key, () ->
                CachedValueProvider.Result.create(compute.get(),
                        PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(project)), false);
    }
}
