package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlToken;

import java.util.Set;
import java.util.regex.Pattern;

/** Regeln, die für Komponenten in Templates und Spezifikationen gleichermaßen gelten. */
class ComponentUsageChecks {
    /** Komponenten-ids müssen gültige Java-Bezeichner sein (Spezifikations-Doku, {@code <component id>}). */
    private static final Pattern COMPONENT_ID = Pattern.compile("_?[a-zA-Z]\\w*");

    private ComponentUsageChecks() {
    }

    static void checkComponentId(final AnnotationHolder holder, final String id) {
        if (!COMPONENT_ID.matcher(id).matches()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Invalid component id '%s': must be a Java identifier".formatted(id)).create();
        }
    }

    static void checkDeprecatedComponent(final AnnotationHolder holder, final XmlFile spec, final String type, final PsiElement anchor) {
        if (ComponentModel.isDeprecated(spec)) {
            holder.newAnnotation(HighlightSeverity.WARNING, "Component '%s' is deprecated".formatted(type))
                    .range(anchor).highlightType(ProblemHighlightType.LIKE_DEPRECATED).create();
        }
    }

    /**
     * Regeln für einen gebundenen Parameter (Template-Attribut oder {@code <binding name>}):
     * <ul>
     *   <li>veralteter Parameter, Binden über einen Alias → Warnung</li>
     *   <li>Parameter schon in der Spezifikation gebunden und kein Literal → Fehler</li>
     *   <li>reservierter Name, kein Literal → Warnung (wird ignoriert; Literale dienen der WYSIWYG-Vorschau)</li>
     *   <li>informeller Parameter bei {@code allow-informal-parameters="no"}, kein Literal → Fehler (nur im Template)</li>
     * </ul>
     */
    static void checkParameterUsage(final AnnotationHolder holder, final XmlFile spec, final String type, final String name,
                                    final boolean literal, final Set<String> specBound, final PsiElement anchor) {
        final ComponentParameter parameter = ComponentModel.findParameter(spec, name);
        if (parameter != null) {
            if (parameter.isAlias(name) && !parameter.name().equalsIgnoreCase(name)) {
                holder.newAnnotation(HighlightSeverity.WARNING, "Parameter alias '%s' of component '%s' is deprecated, use '%s'"
                        .formatted(name, type, parameter.name())).range(anchor).highlightType(ProblemHighlightType.LIKE_DEPRECATED).create();
            } else if (parameter.deprecated()) {
                holder.newAnnotation(HighlightSeverity.WARNING, "Parameter '%s' of component '%s' is deprecated".formatted(name, type))
                        .range(anchor).highlightType(ProblemHighlightType.LIKE_DEPRECATED).create();
            }
            if (isBound(parameter, specBound) && !literal) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Parameter '%s' is already bound in the specification".formatted(name))
                        .range(anchor).create();
            }
            return;
        }
        if (literal) return;
        if (ComponentModel.getReservedParameters(spec).contains(ComponentModel.normalize(name))) {
            holder.newAnnotation(HighlightSeverity.WARNING, "'%s' is reserved by component '%s' and will be ignored".formatted(name, type))
                    .range(anchor).create();
        } else if (!ComponentModel.allowsInformalParameters(spec) && anchor instanceof XmlToken) {
            // nur im Template; in der Spezifikation meldet das bereits die Parameter-Referenz
            holder.newAnnotation(HighlightSeverity.ERROR, "Component '%s' does not allow informal parameters".formatted(type))
                    .range(anchor).create();
        }
    }

    /** Ist der Parameter unter seinem Namen oder einem Alias in den (normalisierten) gebundenen Namen enthalten? */
    static boolean isBound(final ComponentParameter parameter, final Set<String> bound) {
        return bound.contains(ComponentModel.normalize(parameter.name()))
                || parameter.aliases().stream().anyMatch(alias -> bound.contains(ComponentModel.normalize(alias)));
    }
}
