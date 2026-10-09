package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * Formaler Parameter einer Komponente ({@code <parameter>} in der .jwc oder {@code @Parameter} in der Klasse).
 */
public record ComponentParameter(String name,
                                 boolean required,
                                 List<String> aliases,
                                 PsiElement declaration) {

    public boolean matches(final String attributeName) {
        return name.equalsIgnoreCase(attributeName)
                || aliases.stream().anyMatch(a -> a.equalsIgnoreCase(attributeName));
    }
}
