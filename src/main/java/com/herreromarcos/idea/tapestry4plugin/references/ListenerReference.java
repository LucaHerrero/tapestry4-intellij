package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.JavaClasses;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/** {@code listener:onSubmit} → Listener-Methode der Seiten-/Komponentenklasse. */
public class ListenerReference extends TapestryReferenceBase {

    public ListenerReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    @Override
    public @Nullable PsiElement resolve() {
        final PsiClass cls = context().effectiveClass();
        if (cls == null) return null;
        // "A listener method is always a public instance method" (User's Guide, "Listener Methods")
        for (final PsiMethod m : cls.findMethodsByName(getValue().trim(), true)) {
            if (isListenerCandidate(m)) return m;
        }
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final PsiClass cls = context().declaredClass();
        if (cls == null) return EMPTY_ARRAY;
        final ProjectFileIndex index = ProjectFileIndex.getInstance(cls.getProject());
        final Map<String, LookupElement> result = new LinkedHashMap<>();
        for (final PsiMethod m : cls.getAllMethods()) {
            if (!isListenerCandidate(m)) continue;
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
        final String name = getValue().trim();
        final String owner = cls != null ? cls.getName() : "page class";
        final boolean exists = cls != null && cls.findMethodsByName(name, true).length > 0;
        return exists ? "Listener method '%s' in %s must be a public instance method".formatted(name, owner)
                : "Listener method '%s' not found in %s".formatted(name, owner);
    }

    private static boolean isListenerCandidate(PsiMethod method) {
        return !method.isConstructor() && method.hasModifierProperty(PsiModifier.PUBLIC) && !method.hasModifierProperty(PsiModifier.STATIC);
    }
}
