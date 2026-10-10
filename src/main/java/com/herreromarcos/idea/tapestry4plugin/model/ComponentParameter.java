package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * Formaler Parameter einer Komponente ({@code <parameter>} in der .jwc oder {@code @Parameter} in der Klasse).
 *
 * @param aliases    veraltete Alternativnamen – Binden darüber erzeugt laut Doku eine Warnung
 * @param deprecated {@code deprecated="yes"} bzw. {@code @Deprecated}: Binden erzeugt eine Warnung
 */
public record ComponentParameter(String name,
                                 boolean required,
                                 List<String> aliases,
                                 boolean deprecated,
                                 PsiElement declaration) {

    public boolean matches(String attributeName) {
        return name.equalsIgnoreCase(attributeName) || isAlias(attributeName);
    }

    public boolean isAlias(String attributeName) {
        return aliases.stream().anyMatch(a -> a.equalsIgnoreCase(attributeName));
    }
}
