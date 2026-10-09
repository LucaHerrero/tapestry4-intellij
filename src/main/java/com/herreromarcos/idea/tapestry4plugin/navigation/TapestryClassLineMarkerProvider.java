package com.herreromarcos.idea.tapestry4plugin.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/** Gutter-Icon an Seiten-/Komponentenklassen → .page/.jwc und HTML-Template. */
public class TapestryClassLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull final PsiElement element,
                                            @NotNull final Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof PsiIdentifier) || !(element.getParent() instanceof final PsiClass psiClass)) return;
        if (psiClass.getNameIdentifier() != element) return;
        final List<PsiElement> targets = TapestryNavigation.relatedToClass(psiClass);
        if (targets.isEmpty()) return;
        result.add(NavigationGutterIconBuilder.create(TapestryIcons.TAPESTRY)
                .setTargets(targets)
                .setTooltipText("Navigate to Tapestry specification / template")
                .setPopupTitle("Tapestry Files")
                .createLineMarkerInfo(element));
    }
}
