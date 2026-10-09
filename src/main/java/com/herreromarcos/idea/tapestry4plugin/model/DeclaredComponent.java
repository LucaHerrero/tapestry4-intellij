package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * Eine in der Spezifikation ({@code <component id="..." type="...">}) oder per {@code @Component}
 * in der Klasse deklarierte Komponente.
 *
 * @param declaration   das id-Attribut (XmlAttributeValue) bzw. die annotierte Methode
 * @param boundParameters Parameter, die bereits in Spezifikation/Annotation gebunden sind
 */
public record DeclaredComponent(String id,
                                @Nullable String type,
                                @Nullable String copyOf,
                                PsiElement declaration,
                                Set<String> boundParameters) {
}
