package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.PlatformIcons;
import com.herreromarcos.idea.tapestry4plugin.model.JavaClasses;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code listener:onSubmit} → Listener-Methode der Seiten-/Komponentenklasse. */
public class ListenerReference extends TapestryReferenceBase {

    public ListenerReference(@NotNull final PsiElement element, @NotNull final TextRange range) {
        super(element, range);
    }

    @Override
    public @Nullable PsiElement resolve() {
        final PsiClass cls = context().effectiveClass();
        if (cls == null) return null;
        final PsiMethod[] methods = cls.findMethodsByName(getValue().trim(), true);
        for (final PsiMethod m : methods) {
            if (m.hasModifierProperty(PsiModifier.PUBLIC)) return m;
        }
        return methods.length > 0 ? methods[0] : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final PsiClass cls = context().declaredClass();
        if (cls == null) return EMPTY_ARRAY;
        final ProjectFileIndex index = ProjectFileIndex.getInstance(cls.getProject());
        final Map<String, LookupElement> result = new LinkedHashMap<>();
        for (final PsiMethod m : cls.getAllMethods()) {
            if (m.isConstructor() || !m.hasModifierProperty(PsiModifier.PUBLIC) || m.hasModifierProperty(PsiModifier.STATIC)) continue;
            if (PropertyUtilBase.isSimplePropertyAccessor(m)) continue;
            final PsiClass owner = m.getContainingClass();
            final PsiFile ownerFile = owner != null ? owner.getContainingFile() : null;
            final VirtualFile vf = ownerFile != null ? ownerFile.getVirtualFile() : null;
            if (vf == null || !index.isInContent(vf)) continue;
            result.putIfAbsent(m.getName(), LookupElementBuilder.create(m, m.getName()).withIcon(PlatformIcons.METHOD_ICON)
                    .withTypeText(owner.getName(), true));
        }
        return result.values().toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        final TapestryContext ctx = context();
        return ctx.declaredClass() != null && JavaClasses.isHierarchyResolved(ctx.declaredClass())
                ? HighlightSeverity.ERROR : null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final PsiClass cls = context().declaredClass();
        return "Listener method '%s' not found in %s".formatted(getValue().trim(), cls != null ? cls.getName() : "page class");
    }
}
