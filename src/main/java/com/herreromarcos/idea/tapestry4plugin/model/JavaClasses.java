package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** Java-Klassen finden und prüfen. */
public class JavaClasses {
    private JavaClasses() {
    }

    /** Sucht zuerst im Resolve-Scope des Kontexts, dann im ganzen Projekt. */
    public static @Nullable PsiClass find(@NotNull final String fqn, @Nullable final PsiElement context) {
        if (context == null) return null;
        final Project project = context.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass result = facade.findClass(fqn, context.getResolveScope());
        return result != null ? result : facade.findClass(fqn, GlobalSearchScope.allScope(project));
    }

    /** Sind alle Oberklassen auflösbar? Nur dann darf "nicht gefunden" als Fehler gemeldet werden. */
    public static boolean isHierarchyResolved(@NotNull final PsiClass psiClass) {
        final Set<PsiClass> visited = new HashSet<>();
        for (PsiClass current = psiClass; current != null && visited.add(current); current = current.getSuperClass()) {
            for (final PsiClassType type : current.getExtendsListTypes()) {
                if (type.resolve() == null) return false;
            }
        }
        return true;
    }
}
