package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.ApplicationStateObjects;
import com.herreromarcos.idea.tapestry4plugin.model.JavaClasses;
import com.herreromarcos.idea.tapestry4plugin.references.MessageKeyReference;
import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.ANNOTATIONS;
import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.ANNOTATION_PARAMETER;

/**
 * Annotationen ohne Wert, deren Schlüssel Tapestry aus dem Methodennamen ableitet (Annotations-Referenz):
 * {@code @Message} → Message-Key, {@code @InjectState}/{@code @InjectStateFlag} → Application State Object.
 * Mit Wert prüfen das die Referenzen auf dem String. Außerdem: {@code @Parameter(required = true)} darf kein
 * {@code defaultValue} haben ({@code EstablishDefaultParameterValuesVisitor}).
 */
class AnnotationChecks {
    private static final String MESSAGE = ANNOTATIONS + "Message";
    private static final String INJECT_STATE = ANNOTATIONS + "InjectState";
    private static final String INJECT_STATE_FLAG = ANNOTATIONS + "InjectStateFlag";
    private static final String STATE_FLAG_SUFFIX = "-exists";

    private AnnotationChecks() {
    }

    static void checkAnnotation(@NotNull PsiAnnotation annotation, @NotNull AnnotationHolder holder) {
        final String name = annotation.getQualifiedName();
        if (name == null || !name.startsWith(ANNOTATIONS)) return;
        if (!(annotation.getOwner() instanceof final PsiElement owner) || !(owner.getParent() instanceof final PsiMethod method)) return;
        final PsiElement anchor = annotation.getNameReferenceElement() != null ? annotation.getNameReferenceElement() : annotation;
        if (ANNOTATION_PARAMETER.equals(name)) {
            checkRequiredWithDefault(annotation, method, anchor, holder);
            return;
        }
        if (annotation.findDeclaredAttributeValue("value") != null) return;
        final String derived = keyName(method.getName());
        switch (name) {
            case MESSAGE -> checkMessageKey(method, derived, anchor, holder);
            case INJECT_STATE -> checkStateObject(method, derived, anchor, holder);
            case INJECT_STATE_FLAG -> checkStateObject(method, StringUtil.trimEnd(derived, STATE_FLAG_SUFFIX), anchor, holder);
            default -> {
            }
        }
    }

    private static void checkRequiredWithDefault(PsiAnnotation annotation, PsiMethod method, PsiElement anchor, AnnotationHolder holder) {
        if (!Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "required"))
                || StringUtil.isEmpty(AnnotationUtil.getStringAttributeValue(annotation, "defaultValue"))) return;
        final String explicitName = StringUtil.nullize(AnnotationUtil.getStringAttributeValue(annotation, "name"));
        final String parameter = explicitName != null ? explicitName : JavaClasses.propertyName(method);
        holder.newAnnotation(HighlightSeverity.ERROR, "Required parameter '%s' must not have a default value"
                .formatted(String.valueOf(parameter))).range(anchor).create();
    }

    private static void checkMessageKey(PsiMethod method, String key, PsiElement anchor, AnnotationHolder holder) {
        final PsiClass cls = PsiTreeUtil.getParentOfType(method, PsiClass.class);
        if (cls == null) return;
        final List<PropertiesFile> catalogs = MessageKeyReference.catalogs(cls);
        if (catalogs.isEmpty() || MessageKeyReference.containsKey(catalogs, key)) return;
        holder.newAnnotation(HighlightSeverity.WARNING, "Message key '%s' (derived from %s) not found".formatted(key, method.getName()))
                .range(anchor).create();
    }

    private static void checkStateObject(PsiMethod method, String objectName, PsiElement anchor, AnnotationHolder holder) {
        final Map<String, ?> stateObjects = ApplicationStateObjects.getAll(method.getProject());
        if (stateObjects.isEmpty() || stateObjects.containsKey(objectName)) return;
        holder.newAnnotation(HighlightSeverity.WARNING, "Unknown application state object '%s' (derived from %s)"
                .formatted(objectName, method.getName())).range(anchor).create();
    }

    /**
     * Schlüssel aus dem Methodennamen wie Tapestrys {@code AnnotationUtils.convertMethodNameToKeyName}: ein führendes
     * "get" entfällt, der erste Buchstabe wird klein, jeder weitere Großbuchstabe wird zu "-" plus Kleinbuchstabe
     * ({@code getPageTitle} → {@code page-title}).
     */
    static @NotNull String keyName(@NotNull String methodName) {
        final String base = methodName.startsWith("get") && methodName.length() > 3 ? methodName.substring(3) : methodName;
        final StringBuilder result = new StringBuilder();
        for (int i = 0; i < base.length(); i++) {
            final char c = base.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) result.append('-');
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }
}
