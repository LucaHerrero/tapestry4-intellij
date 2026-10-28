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
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PropertyUtilBase;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Java-Klassen finden und prüfen. */
public class JavaClasses {
    private static final String[] GETTER_PREFIXES = {"get", "is"};

    private JavaClasses() {
    }

    /** Sucht zuerst im Resolve-Scope des Kontexts, dann im ganzen Projekt. */
    public static @Nullable PsiClass find(@NotNull String fqn, @Nullable PsiElement context) {
        if (context == null) return null;
        final Project project = context.getProject();
        final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
        final PsiClass result = facade.findClass(fqn, context.getResolveScope());
        return result != null ? result : facade.findClass(fqn, GlobalSearchScope.allScope(project));
    }

    /**
     * Getter einer Eigenschaft, wie OGNL 2.6.9 ihn findet ({@code OgnlRuntime.getGetMethod}): zuerst nach
     * Bean-Konvention, sonst jede Methode {@code getX()}/{@code isX()} ohne Parameter – {@code is} also auch bei
     * {@code Boolean} oder anderen Rückgabetypen, die der {@link Introspector} nicht als Eigenschaft anerkennt.
     */
    public static @Nullable PsiMethod findGetter(@NotNull PsiClass psiClass, @NotNull String property) {
        final PsiMethod getter = PropertyUtilBase.findPropertyGetter(psiClass, property, false, true);
        if (getter != null) return getter;
        final String capitalized = Character.toUpperCase(property.charAt(0)) + property.substring(1);
        for (final String prefix : GETTER_PREFIXES) {
            for (final PsiMethod method : psiClass.findMethodsByName(prefix + capitalized, true)) {
                if (isLenientGetter(method)) return method;
            }
        }
        return null;
    }

    /**
     * Eigenschaftsname eines Getters oder Setters ({@code isActive} → {@code active}), {@code null} für andere
     * Methoden. Wie {@link #findGetter} großzügiger als die Bean-Konvention, entsprechend OGNL und Tapestrys
     * {@code AnnotationUtils.getPropertyName}.
     */
    public static @Nullable String propertyName(@NotNull PsiMethod method) {
        final String standard = PropertyUtilBase.getPropertyName(method);
        if (standard != null) return standard;
        if (!isLenientGetter(method)) return null;
        for (final String prefix : GETTER_PREFIXES) {
            final String name = method.getName();
            if (name.length() > prefix.length() && name.startsWith(prefix) && Character.isUpperCase(name.charAt(prefix.length()))) {
                return Introspector.decapitalize(name.substring(prefix.length()));
            }
        }
        return null;
    }

    private static boolean isLenientGetter(PsiMethod method) {
        return method.getParameterList().isEmpty() && !method.hasModifierProperty(PsiModifier.STATIC)
                && method.getReturnType() != null && !PsiTypes.voidType().equals(method.getReturnType());
    }

    /** Wert zu {@code key} aus {@code @Meta({"key=value", ...})} direkt an der Klasse, sonst {@code null}. */
    public static @Nullable String metaAnnotationValue(@NotNull PsiClass psiClass, @NotNull String key) {
        final PsiLiteralExpression entry = metaAnnotationEntries(psiClass).get(key);
        return entry != null && entry.getValue() instanceof final String text ? StringUtils.substringAfter(text, "=").trim() : null;
    }

    /** Einträge {@code "key=value"} aus {@code @Meta} direkt an der Klasse: Schlüssel → String-Literal. */
    public static @NotNull Map<String, PsiLiteralExpression> metaAnnotationEntries(@NotNull PsiClass psiClass) {
        final PsiModifierList modifiers = psiClass.getModifierList();
        final PsiAnnotation meta = modifiers != null ? modifiers.findAnnotation(TapestryConstants.ANNOTATION_META) : null;
        if (meta == null) return Map.of();
        final Map<String, PsiLiteralExpression> result = new LinkedHashMap<>();
        for (final PsiAnnotationMemberValue value : AnnotationUtil.arrayAttributeValues(meta.findDeclaredAttributeValue("value"))) {
            if (value instanceof final PsiLiteralExpression literal && literal.getValue() instanceof final String entry && entry.contains("=")) {
                result.putIfAbsent(StringUtils.substringBefore(entry, "=").trim(), literal);
            }
        }
        return result;
    }

    /** Sind alle Oberklassen auflösbar? Nur dann darf "nicht gefunden" als Fehler gemeldet werden. */
    public static boolean isHierarchyResolved(@NotNull PsiClass psiClass) {
        final Set<PsiClass> visited = new HashSet<>();
        for (PsiClass current = psiClass; current != null && visited.add(current); current = current.getSuperClass()) {
            for (final PsiClassType type : current.getExtendsListTypes()) {
                if (type.resolve() == null) return false;
            }
        }
        return true;
    }
}
