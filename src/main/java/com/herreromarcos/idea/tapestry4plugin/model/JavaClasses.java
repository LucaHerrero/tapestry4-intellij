package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.search.GlobalSearchScope;
import org.apache.commons.lang3.StringUtils;
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

    /** Wert zu {@code key} aus {@code @Meta({"key=value", ...})} direkt an der Klasse, sonst {@code null}. */
    public static @Nullable String metaAnnotationValue(@NotNull final PsiClass psiClass, @NotNull final String key) {
        final PsiModifierList modifiers = psiClass.getModifierList();
        final PsiAnnotation meta = modifiers != null ? modifiers.findAnnotation(TapestryConstants.ANNOTATION_META) : null;
        if (meta == null) return null;
        for (final PsiAnnotationMemberValue value : AnnotationUtil.arrayAttributeValues(meta.findDeclaredAttributeValue("value"))) {
            if (value instanceof final PsiLiteralExpression literal && literal.getValue() instanceof final String entry) {
                if (entry.contains("=") && key.equals(StringUtils.substringBefore(entry, "=").trim())) {
                    return StringUtils.substringAfter(entry, "=").trim();
                }
            }
        }
        return null;
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
