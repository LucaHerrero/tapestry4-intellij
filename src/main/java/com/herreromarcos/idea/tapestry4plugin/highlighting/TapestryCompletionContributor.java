package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.XmlPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ProcessingContext;
import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryFiles;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Bietet Binding-Präfixe ({@code ognl:}, {@code listener:}, {@code message:} …) in Parameterwerten an. */
public class TapestryCompletionContributor extends CompletionContributor {
    private static final Set<String> SPEC_BINDING_ATTRIBUTES = Set.of("binding.value", "set.value", "property.initial-value",
            "parameter.default-value");

    public TapestryCompletionContributor() {
        extend(CompletionType.BASIC, PlatformPatterns.psiElement().inside(XmlPatterns.xmlAttributeValue()), new CompletionProvider<>() {
            @Override
            protected void addCompletions(@NotNull final CompletionParameters parameters, @NotNull final ProcessingContext context,
                                          @NotNull final CompletionResultSet result) {
                final PsiElement position = parameters.getPosition();
                final XmlAttributeValue value = PsiTreeUtil.getParentOfType(position, XmlAttributeValue.class);
                if (value == null || !(value.getParent() instanceof final XmlAttribute attribute)) return;
                if (!acceptsBinding(attribute, parameters.getOriginalFile())) return;

                final int caret = parameters.getOffset() - value.getTextRange().getStartOffset();
                final String text = value.getText();
                String beforeCaret = caret > 0 && caret <= text.length() ? text.substring(0, caret) : "";
                beforeCaret = beforeCaret.replaceFirst("^[\"']", "");
                if (beforeCaret.contains(":") || !beforeCaret.chars().allMatch(Character::isLetter)) return;

                final CompletionResultSet prefixed = result.withPrefixMatcher(beforeCaret);
                for (final String prefix : TapestryConstants.BINDING_PREFIXES) {
                    prefixed.addElement(LookupElementBuilder.create(prefix + ":")
                            .withIcon(TapestryIcons.TAPESTRY)
                            .withTypeText("binding prefix", true)
                            .withInsertHandler((ctx, item) ->
                                    AutoPopupController.getInstance(ctx.getProject()).scheduleAutoPopup(ctx.getEditor())));
                }
            }
        });
    }

    private static boolean acceptsBinding(final XmlAttribute attribute, final PsiFile file) {
        final XmlTag tag = attribute.getParent();
        if (tag == null) return false;
        if (TapestryFiles.isTemplateFile(file)) {
            return !TapestryConstants.JWCID.equalsIgnoreCase(attribute.getName()) && tag.getAttribute(TapestryConstants.JWCID) != null;
        }
        return TapestryFiles.isSpecFile(file) && SPEC_BINDING_ATTRIBUTES.contains("%s.%s".formatted(tag.getName(), attribute.getName()));
    }
}
