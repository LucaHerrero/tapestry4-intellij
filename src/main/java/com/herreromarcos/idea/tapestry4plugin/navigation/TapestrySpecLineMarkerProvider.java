package com.herreromarcos.idea.tapestry4plugin.navigation;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.SpecKind;
import com.herreromarcos.idea.tapestry4plugin.model.SpecXml;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider;
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Gutter-Icons in .page/.jwc:
 * am Wurzel-Tag → Template und Klasse, an {@code <component id="...">} → Verwendungen im Template.
 */
public class TapestrySpecLineMarkerProvider extends RelatedItemLineMarkerProvider {

    @Override
    protected void collectNavigationMarkers(@NotNull final PsiElement element,
                                            @NotNull final Collection<? super RelatedItemLineMarkerInfo<?>> result) {
        if (!(element instanceof final XmlToken token) || token.getTokenType() != XmlTokenType.XML_NAME) return;
        if (!(token.getParent() instanceof final XmlTag tag) || tag.getFirstChild() == null) return;
        // nur das Namens-Token des öffnenden Tags
        if (token.getPrevSibling() == null || token.getPrevSibling().getNode().getElementType() != XmlTokenType.XML_START_TAG_START) return;
        if (!(tag.getContainingFile() instanceof final XmlFile file) || !TapestryFiles.isSpecFile(file)) return;
        final SpecKind kind = TapestryFiles.getSpecKind(file);
        if (kind != SpecKind.PAGE && kind != SpecKind.COMPONENT) return;

        if (tag == file.getRootTag()) {
            final List<PsiElement> targets = TapestryNavigation.relatedToFile(file);
            if (!targets.isEmpty()) {
                result.add(NavigationGutterIconBuilder.create(TapestryIcons.TAPESTRY)
                        .setTargets(targets)
                        .setTooltipText("Navigate to template / class")
                        .setPopupTitle("Tapestry Files")
                        .createLineMarkerInfo(token));
            }
        } else if (TapestryConstants.TAG_COMPONENT.equals(tag.getName()) && tag.getParentTag() == file.getRootTag()) {
            final String id = SpecXml.attr(tag, TapestryConstants.ATTR_ID);
            if (id == null) return;
            final List<PsiElement> usages = TapestryNavigation.templateUsages(file, id);
            if (!usages.isEmpty()) {
                result.add(NavigationGutterIconBuilder.create(TapestryIcons.COMPONENT)
                        .setTargets(usages)
                        .setTooltipText("Navigate to usage in template")
                        .setPopupTitle("Template Usages of '%s'".formatted(id))
                        .createLineMarkerInfo(token));
            }
        }
    }
}
