package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PlatformIcons;
import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * {@code message:key} â Eintrag in den Message-Katalogen: {@code <Seite>.properties} (+ Locale-Varianten)
 * neben Spezifikation/Template sowie die anwendungsweiten {@code <app>.properties} neben der .application.
 */
public class MessageKeyReference extends TapestryReferenceBase implements PsiPolyVariantReference {
    /** Optionales Locale-Suffix: _de, _de_AT, _en_US_POSIX – nicht aber _Admin (das wäre eine andere Seite). */
    private static final String LOCALE_SUFFIX = "(_[a-z]{2,3}(_([A-Z]{2}|[0-9]{3})(_\\w+)?)?)?";

    public MessageKeyReference(@NotNull final PsiElement element, @NotNull final TextRange range) {
        super(element, range);
    }

    private List<PropertiesFile> catalogs() {
        final TapestryContext ctx = context();
        final List<PropertiesFile> result = new ArrayList<>();
        final Set<VirtualFile> seen = new HashSet<>();
        for (final PsiFile owner : Arrays.asList(ctx.spec(), ctx.template())) {
            if (owner != null) collect(owner.getOriginalFile().getVirtualFile(), result, seen);
        }
        final PsiFile file = getElement().getContainingFile().getOriginalFile();
        for (final VirtualFile app : FilenameIndex.getAllFilesByExt(file.getProject(), TapestryConstants.EXT_APPLICATION,
                GlobalSearchScope.projectScope(file.getProject()))) {
            collect(app, result, seen);
        }
        return result;
    }

    private void collect(@Nullable final VirtualFile owner, final List<PropertiesFile> result, final Set<VirtualFile> seen) {
        if (owner == null || owner.getParent() == null) return;
        final String base = owner.getNameWithoutExtension();
        final PsiManager manager = getElement().getManager();
        final Pattern catalog = Pattern.compile(Pattern.quote(base) + LOCALE_SUFFIX + "\\.properties");
        for (final VirtualFile child : owner.getParent().getChildren()) {
            if (!catalog.matcher(child.getName()).matches()) continue;
            if (seen.add(child) && manager.findFile(child) instanceof final PropertiesFile pf) result.add(pf);
        }
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(final boolean incompleteCode) {
        final String key = getValue().trim();
        final List<ResolveResult> result = new ArrayList<>();
        for (final PropertiesFile pf : catalogs()) {
            for (final IProperty property : pf.findPropertiesByKey(key)) {
                result.add(new PsiElementResolveResult(property.getPsiElement()));
            }
        }
        return result.toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    public @Nullable PsiElement resolve() {
        final ResolveResult[] results = multiResolve(false);
        return results.length > 0 ? results[0].getElement() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final Map<String, LookupElement> result = new LinkedHashMap<>();
        for (final PropertiesFile pf : catalogs()) {
            for (final IProperty property : pf.getProperties()) {
                final String key = property.getKey();
                if (key != null) {
                    result.putIfAbsent(key, LookupElementBuilder.create(key).withIcon(PlatformIcons.PROPERTY_ICON)
                            .withTailText(" = " + property.getValue(), true).withTypeText(pf.getName(), true));
                }
            }
        }
        return result.values().toArray();
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return catalogs().isEmpty() ? null : HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Message key '%s' not found".formatted(getValue().trim());
    }
}
