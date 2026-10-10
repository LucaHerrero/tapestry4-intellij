package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryPaths;
import com.herreromarcos.idea.tapestry4plugin.model.WebXml;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * {@code message:key} bzw. {@code <span key="...">} → Eintrag im Message-Katalog (User's Guide, "Localization"):
 * <ul>
 *   <li>Katalog der Seite/Komponente: {@code <Name>.properties} (+ Locale-Varianten) neben Spezifikation/Template</li>
 *   <li>Namespace-Katalog: {@code <app>.properties} neben der .application, sowie
 *       {@code WEB-INF/<servlet-name>.properties} – auch ohne .application, sowie {@code <lib>.properties} einer Bibliothek</li>
 * </ul>
 */
public class MessageKeyReference extends TapestryReferenceBase implements PsiPolyVariantReference {

    public MessageKeyReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    /** Alle Kataloge in Suchreihenfolge: Seite/Komponente, Anwendung, Bibliothek, Servlet. */
    private List<PropertiesFile> catalogs() {
        final TapestryContext ctx = context();
        final List<PropertiesFile> result = new ArrayList<>();
        final Set<VirtualFile> seen = new HashSet<>();
        for (final PsiFile owner : Arrays.asList(ctx.spec(), ctx.template())) {
            final VirtualFile vf = owner != null ? owner.getOriginalFile().getVirtualFile() : null;
            if (vf != null) collect(vf.getParent(), TapestryPaths.stripLocale(vf.getNameWithoutExtension()), result, seen);
        }
        collectApplicationCatalogs(result, seen);
        collectLibraryCatalogs(ctx, result, seen);
        collectServletCatalogs(result, seen);
        return result;
    }

    /** {@code <app>.properties} neben jeder .application. */
    private void collectApplicationCatalogs(List<PropertiesFile> result, Set<VirtualFile> seen) {
        final Project project = getElement().getProject();
        for (final VirtualFile app : FilenameIndex.getAllFilesByExt(project, TapestryConstants.EXT_APPLICATION,
                GlobalSearchScope.projectScope(project))) {
            collect(app.getParent(), app.getNameWithoutExtension(), result, seen);
        }
    }

    /** Namespace-Katalog einer Bibliothek: gilt für Seiten/Komponenten im Ordner der .library (und darunter). */
    private void collectLibraryCatalogs(TapestryContext ctx, List<PropertiesFile> result, Set<VirtualFile> seen) {
        final PsiFile owner = ctx.spec() != null ? ctx.spec() : ctx.template();
        final VirtualFile ownerFile = owner != null ? owner.getOriginalFile().getVirtualFile() : null;
        if (ownerFile == null) return;
        final Project project = getElement().getProject();
        for (final VirtualFile library : FilenameIndex.getAllFilesByExt(project, TapestryConstants.EXT_LIBRARY,
                GlobalSearchScope.allScope(project))) {
            final VirtualFile folder = library.getParent();
            if (folder != null && VfsUtilCore.isAncestor(folder, ownerFile, true)) {
                collect(folder, library.getNameWithoutExtension(), result, seen);
            }
        }
    }

    /** {@code WEB-INF/<servlet-name>.properties} bzw. {@code WEB-INF/<servlet-name>/<servlet-name>.properties}. */
    private void collectServletCatalogs(List<PropertiesFile> result, Set<VirtualFile> seen) {
        final VirtualFile vf = getElement().getContainingFile().getOriginalFile().getVirtualFile();
        final VirtualFile webRoot = vf != null ? TapestryPaths.webRoot(vf) : null;
        final VirtualFile webInf = webRoot != null ? webRoot.findChild(TapestryConstants.WEB_INF) : null;
        if (webInf == null) return;
        for (final String servlet : WebXml.servletNames(getElement().getProject(), webInf)) {
            collect(webInf, servlet, result, seen);
            collect(webInf.findChild(servlet), servlet, result, seen);
        }
    }

    /** Katalogdateien {@code base.properties} und {@code base_<locale>.properties} im Verzeichnis. */
    private void collect(@Nullable VirtualFile dir, String base, List<PropertiesFile> result,
                         Set<VirtualFile> seen) {
        if (dir == null || !dir.isDirectory()) return;
        final PsiManager manager = getElement().getManager();
        final Pattern catalog = Pattern.compile(Pattern.quote(base) + TapestryPaths.LOCALE_SUFFIX + "?\\.properties");
        for (final VirtualFile child : dir.getChildren()) {
            if (!catalog.matcher(child.getName()).matches()) continue;
            if (seen.add(child) && manager.findFile(child) instanceof final PropertiesFile pf) result.add(pf);
        }
    }

    @Override
    public ResolveResult @NotNull [] multiResolve(boolean incompleteCode) {
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
