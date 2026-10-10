package com.herreromarcos.idea.tapestry4plugin.html;

import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtilRt;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Attribut-Deskriptor für {@code jwcid} und formale Komponentenparameter in HTML-Templates. */
public class TapestryAttributeDescriptor extends BasicXmlAttributeDescriptor {
    private final String name;
    private final @Nullable PsiElement declaration;

    public TapestryAttributeDescriptor(@NotNull String name, @Nullable PsiElement declaration) {
        this.name = name;
        this.declaration = declaration;
    }

    /** Pflichtparameter prüft der Annotator – sie können auch in der Spezifikation gebunden sein. */
    @Override
    public boolean isRequired() {
        return false;
    }

    @Override
    public boolean isFixed() {
        return false;
    }

    @Override
    public boolean hasIdType() {
        return false;
    }

    @Override
    public boolean hasIdRefType() {
        return false;
    }

    @Override
    public @Nullable String getDefaultValue() {
        return null;
    }

    @Override
    public boolean isEnumerated() {
        return false;
    }

    @Override
    public String @Nullable [] getEnumeratedValues() {
        return ArrayUtilRt.EMPTY_STRING_ARRAY;
    }

    @Override
    public @Nullable PsiElement getDeclaration() {
        return declaration;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void init(PsiElement element) {
    }
}
