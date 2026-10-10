package com.herreromarcos.idea.tapestry4plugin.navigation;

import com.herreromarcos.idea.tapestry4plugin.model.Jwcid;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Ermittelt die zusammengehörigen Dateien einer Seite/Komponente für Gutter-Icons und "Go to Related". */
class TapestryNavigation {
    private TapestryNavigation() {
    }

    static @NotNull List<PsiElement> relatedToClass(@NotNull PsiClass psiClass) {
        final Set<PsiElement> result = new LinkedHashSet<>();
        for (final XmlFile spec : TapestryModel.findSpecsForClass(psiClass)) {
            result.add(spec);
            result.addAll(TapestryModel.findTemplatesForSpec(spec));
        }
        result.addAll(TapestryModel.findTemplatesByClassConvention(psiClass));
        return new ArrayList<>(result);
    }

    static @NotNull List<PsiElement> relatedToFile(@NotNull PsiFile file) {
        final List<PsiElement> result = new ArrayList<>();
        if (!TapestryFiles.isTemplateFile(file) && !TapestryFiles.isSpecFile(file)) return result;
        final TapestryContext ctx = TapestryModel.getContext(file);
        if (TapestryFiles.isTemplateFile(file)) {
            if (ctx.spec() != null) result.add(ctx.spec());
        } else if (ctx.spec() != null) {
            result.addAll(TapestryModel.findTemplatesForSpec(ctx.spec()));
        }
        if (ctx.declaredClass() != null) result.add(ctx.declaredClass());
        return result;
    }

    /** Alle Template-Tags, die eine in der Spezifikation deklarierte Komponente verwenden. */
    static @NotNull List<PsiElement> templateUsages(@NotNull XmlFile spec, @NotNull String componentId) {
        final List<PsiElement> result = new ArrayList<>();
        for (final PsiFile template : TapestryModel.findTemplatesForSpec(spec)) {
            for (final XmlTag tag : PsiTreeUtil.findChildrenOfType(template, XmlTag.class)) {
                final XmlAttributeValue value = TapestryConfiguration.findJwcidValueElement(tag);
                if (value == null) continue;
                final Jwcid jwcid = Jwcid.parse(value.getValue());
                if (jwcid.isDeclaredReference() && componentId.equals(jwcid.id())) result.add(value);
            }
        }
        return result;
    }
}
