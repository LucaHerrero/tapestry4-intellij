package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Verweis auf einen Komponententyp ({@code @Insert}, {@code type="contrib:Table"}) – löst auf die .jwc-Datei auf. */
public class ComponentTypeReference extends TapestryReferenceBase {

    public ComponentTypeReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    @Override
    public @Nullable PsiElement resolve() {
        return TapestryRegistry.resolveComponentType(getValue(), getElement());
    }

    @Override
    public Object @NotNull [] getVariants() {
        final Map<String, VirtualFile> types = TapestryRegistry.getComponentTypes(getElement().getProject());
        final List<LookupElement> result = new ArrayList<>();
        for (final String name : TapestryRegistry.getAllTypeNames(getElement().getProject())) {
            final VirtualFile vf = types.get(name);
            final String origin = vf == null ? "framework" : vf.getPath().contains("!/") ? "library" : "project";
            result.add(LookupElementBuilder.create(name).withIcon(TapestryIcons.COMPONENT).withTypeText(origin, true));
        }
        return result.toArray();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        final String value = getValue();
        final String newName = StringUtil.trimEnd(newElementName, "." + TapestryConstants.EXT_COMPONENT);
        final int separator = StringUtils.lastIndexOfAny(value, "/", ":");
        return super.handleElementRename(separator >= 0 ? value.substring(0, separator + 1) + newName : newName);
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        final String type = getValue().trim();
        if (type.isEmpty()) return HighlightSeverity.ERROR;
        final boolean frameworkPresent = TapestryRegistry.isFrameworkPresent(getElement().getProject());
        if (!frameworkPresent && TapestryConstants.FRAMEWORK_COMPONENTS.contains(StringUtil.trimStart(type, TapestryConstants.FRAMEWORK_NAMESPACE + ":"))) {
            return null;
        }
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final String type = getValue().trim();
        return type.isEmpty() ? "Component type expected after '@'" : "Unknown component type '%s'".formatted(type);
    }
}
