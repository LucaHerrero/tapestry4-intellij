package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/** {@code <binding name="...">} innerhalb von {@code <component>} → formaler Parameter der Komponente. */
public class ParameterNameReference extends TapestryReferenceBase {

    public ParameterNameReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    private @Nullable PsiFile componentSpec() {
        final XmlTag bindingTag = PsiTreeUtil.getParentOfType(getElement(), XmlTag.class);
        final XmlTag componentTag = bindingTag != null ? bindingTag.getParentTag() : null;
        if (componentTag == null || !TAG_COMPONENT.equals(componentTag.getName())) return null;
        final String type = ComponentModel.getComponentTypeOfSpecTag(componentTag);
        return type != null ? TapestryRegistry.resolveComponentType(type, getElement()) : null;
    }

    @Override
    public @Nullable PsiElement resolve() {
        final PsiFile spec = componentSpec();
        final ComponentParameter parameter = spec != null ? ComponentModel.findParameter(spec, getValue().trim()) : null;
        return parameter != null ? parameter.declaration() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final PsiFile spec = componentSpec();
        if (spec == null) return EMPTY_ARRAY;
        final List<LookupElement> result = new ArrayList<>();
        for (final ComponentParameter p : ComponentModel.getParameters(spec)) {
            final LookupElementBuilder builder = LookupElementBuilder.create(p.name()).withIcon(TapestryIcons.TAPESTRY)
                    .withTypeText(spec.getName(), true);
            result.add(p.required() ? builder.bold().withTailText(" (required)", true) : builder);
        }
        return result.toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        final PsiFile spec = componentSpec();
        return spec != null && !ComponentModel.allowsInformalParameters(spec) ? HighlightSeverity.ERROR : null;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final PsiFile spec = componentSpec();
        return "Component %s has no parameter '%s' and does not allow informal parameters".formatted(spec != null ? spec.getName() : "", getValue().trim());
    }
}
