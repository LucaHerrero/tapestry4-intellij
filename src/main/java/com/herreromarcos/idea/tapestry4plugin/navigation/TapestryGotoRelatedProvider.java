package com.herreromarcos.idea.tapestry4plugin.navigation;

import com.intellij.navigation.GotoRelatedItem;
import com.intellij.navigation.GotoRelatedProvider;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** "Navigate | Related Symbol…" (Strg+Alt+Pos1): Template ↔ Spezifikation ↔ Klasse. */
public class TapestryGotoRelatedProvider extends GotoRelatedProvider {
    private static final String GROUP = "Tapestry";

    @Override
    public @NotNull List<? extends GotoRelatedItem> getItems(@NotNull PsiElement context) {
        final List<PsiElement> targets;
        final PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class, false);
        if (psiClass != null) {
            targets = TapestryNavigation.relatedToClass(psiClass);
        } else {
            final PsiFile file = context.getContainingFile();
            targets = file != null ? TapestryNavigation.relatedToFile(file) : List.of();
        }
        final List<GotoRelatedItem> items = new ArrayList<>();
        for (final PsiElement target : targets) {
            items.add(new GotoRelatedItem(target, GROUP));
        }
        return items;
    }
}
