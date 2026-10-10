package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * Script-Spezifikationen (.script): {@code <include-script resource-path="/org/.../x.js">} ist ein Pfad
 * im Classpath (User's Guide, "Script Template Specification DTDs").
 */
class ScriptReferenceProvider extends PsiReferenceProvider {

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        if (!(element instanceof final XmlAttributeValue value) || !(value.getParent() instanceof final XmlAttribute attribute)) {
            return PsiReference.EMPTY_ARRAY;
        }
        final XmlTag tag = attribute.getParent();
        if (tag == null || !"include-script".equals(tag.getName()) || !"resource-path".equals(attribute.getName())
                || !isScriptFile(element.getContainingFile())) {
            return PsiReference.EMPTY_ARRAY;
        }
        return SpecPathReferences.create(value, ElementManipulators.getValueText(value),
                ElementManipulators.getValueTextRange(value).getStartOffset(), false);
    }

    private static boolean isScriptFile(PsiFile file) {
        return file instanceof final XmlFile xml && xml.getRootTag() != null && TapestryConstants.ROOT_SCRIPT.equals(xml.getRootTag().getName());
    }
}
