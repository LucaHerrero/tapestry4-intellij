package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Zuordnung der Bestandteile einer Seite/Komponente: HTML-Template ↔ Spezifikation (.page/.jwc) ↔ Java-Klasse.
 * Ergebnisse werden über {@link CachedValuesManager} gecacht.
 */
public class TapestryModel {
    private static final Key<CachedValue<Map<String, List<VirtualFile>>>> SPECS_BY_CLASS_KEY = Key.create("tapestry4.specsByClass");

    private TapestryModel() {
    }

    // ------------------------------------------------------------------ Kontext

    /** Kontext zu einem Template oder einer Spezifikation; für andere Dateien ein leerer Kontext. */
    public static @NotNull TapestryContext getContext(@NotNull PsiFile anyFile) {
        final PsiFile file = anyFile.getOriginalFile();
        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(computeContext(file),
                        PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(file.getProject())));
    }

    /**
     * Kontext einer Seiten-/Komponentenklasse (für Referenzen in Annotationen): über die Spezifikation, deren
     * class-Attribut auf die Klasse zeigt, sonst über das Template per *-class-packages, sonst nur die Klasse.
     */
    public static @NotNull TapestryContext getContext(@NotNull PsiClass psiClass) {
        return CachedValuesManager.getCachedValue(psiClass, () -> {
            final XmlFile spec = ContainerUtil.getFirstItem(findSpecsForClass(psiClass));
            final PsiFile template = spec == null ? ContainerUtil.getFirstItem(findTemplatesByClassConvention(psiClass)) : null;
            final TapestryContext ctx = spec != null ? getContext(spec)
                    : template != null ? getContext(template)
                    : new TapestryContext(null, null, psiClass, psiClass, SpecKind.PAGE);
            return CachedValueProvider.Result.create(ctx,
                    PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(psiClass.getProject()));
        });
    }

    private static TapestryContext computeContext(PsiFile file) {
        final PsiFile template;
        final XmlFile spec;
        if (TapestryFiles.isTemplateFile(file)) {
            template = file;
            spec = findSpecForTemplate(file);
        } else if (TapestryFiles.isSpecFile(file)) {
            spec = (XmlFile) file;
            template = ContainerUtil.getFirstItem(findTemplatesForSpec(spec));
        } else {
            return TapestryContext.empty();
        }
        final SpecKind kind = TapestryFiles.getSpecKind(spec) == SpecKind.COMPONENT ? SpecKind.COMPONENT : SpecKind.PAGE;
        final PsiFile owner = spec != null ? spec : template;
        final PsiClass declared = findDeclaredClass(spec, owner, kind);
        final PsiClass effective = declared != null ? declared
                : JavaClasses.find(kind == SpecKind.COMPONENT ? DEFAULT_COMPONENT_CLASS : DEFAULT_PAGE_CLASS, owner);
        return new TapestryContext(template, spec, declared, effective, kind);
    }

    /**
     * Klassensuche laut User's Guide ("Determining the Page Class"):
     * <ol>
     *   <li>class-Attribut der Spezifikation</li>
     *   <li>Pakete aus *-class-packages, mit dem Seitennamen inkl. Ordnern ({@code admin/EditUser} → {@code admin.EditUser})</li>
     *   <li>das Default-Paket</li>
     *   <li>für Seiten: {@code org.apache.tapestry.default-page-class}</li>
     * </ol>
     * Ohne Spezifikation ist unklar, ob Seite oder Komponente – dann werden beide Paketlisten durchsucht.
     */
    private static @Nullable PsiClass findDeclaredClass(@Nullable XmlFile spec, @NotNull PsiFile owner,
                                                       @NotNull SpecKind kind) {
        final XmlTag root = SpecXml.rootTag(spec);
        final String className = root != null ? SpecXml.attr(root, ATTR_CLASS) : null;
        if (className != null) {
            final PsiClass cls = JavaClasses.find(className, spec);
            if (cls != null) return cls;
        }
        final List<String> names = logicalClassNames(owner);
        if (names.isEmpty()) return null;
        final List<String> metaKeys = spec == null ? List.of(META_PAGE_PACKAGES, META_COMPONENT_PACKAGES)
                : List.of(kind == SpecKind.COMPONENT ? META_COMPONENT_PACKAGES : META_PAGE_PACKAGES);
        for (final String metaKey : metaKeys) {
            for (final String pkg : TapestryConfiguration.getNamespaceMeta(owner, metaKey)) {
                for (final String name : names) {
                    final PsiClass cls = JavaClasses.find("%s.%s".formatted(pkg, name), owner);
                    if (cls != null) return cls;
                }
            }
        }
        for (final String name : names) {
            final PsiClass cls = JavaClasses.find(name, owner);
            if (cls != null) return cls;
        }
        if (kind == SpecKind.PAGE) {
            for (final String defaultClass : TapestryConfiguration.getNamespaceMeta(owner, META_DEFAULT_PAGE_CLASS)) {
                final PsiClass cls = JavaClasses.find(defaultClass, owner);
                if (cls != null) return cls;
            }
        }
        return null;
    }

    /** Seitenname als Klassenname: zuerst mit Ordnern ({@code admin.EditUser}), dann nur der einfache Name. */
    private static List<String> logicalClassNames(PsiFile owner) {
        final VirtualFile vf = owner.getOriginalFile().getVirtualFile();
        if (vf == null) return List.of();
        final Set<String> names = new LinkedHashSet<>();
        final String pagePath = TapestryPaths.logicalPagePath(vf);
        if (pagePath != null) names.add(TapestryPaths.stripLocale(pagePath).replace('/', '.'));
        names.add(TapestryPaths.stripLocale(vf.getNameWithoutExtension()));
        return new ArrayList<>(names);
    }

    // ------------------------------------------------------------------ Template <-> Spezifikation

    /**
     * Spezifikation gleichen Namens (.page oder .jwc) mit dem ähnlichsten Pfad. Lokalisierte Templates
     * ({@code Home_de.html}) gehören zur Spezifikation ohne Locale ({@code Home.page}).
     */
    public static @Nullable XmlFile findSpecForTemplate(@NotNull PsiFile template) {
        final VirtualFile vf = template.getOriginalFile().getVirtualFile();
        if (vf == null) return null;
        final Project project = template.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final Set<String> baseNames = new LinkedHashSet<>(List.of(vf.getNameWithoutExtension(),
                TapestryPaths.stripLocale(vf.getNameWithoutExtension())));
        for (final String baseName : baseNames) {
            final List<VirtualFile> candidates = new ArrayList<>();
            for (final String ext : List.of(EXT_PAGE, EXT_COMPONENT)) {
                candidates.addAll(FilenameIndex.getVirtualFilesByName("%s.%s".formatted(baseName, ext), scope));
            }
            final VirtualFile closest = TapestryPaths.closest(vf, candidates);
            if (closest != null && PsiManager.getInstance(project).findFile(closest) instanceof final XmlFile xml) return xml;
        }
        return null;
    }

    /** Templates gleichen Namens (Endung laut template-extension) mit dem ähnlichsten Pfad (bei Gleichstand alle). */
    public static @NotNull List<PsiFile> findTemplatesForSpec(@NotNull XmlFile spec) {
        final VirtualFile vf = spec.getOriginalFile().getVirtualFile();
        if (vf == null) return List.of();
        final Collection<VirtualFile> candidates = FilenameIndex.getVirtualFilesByName(
                "%s.%s".formatted(vf.getNameWithoutExtension(), TapestryConfiguration.getTemplateExtension(spec)), GlobalSearchScope.allScope(spec.getProject()));
        return toPsiFiles(spec.getProject(), TapestryPaths.allClosest(vf, candidates));
    }

    // ------------------------------------------------------------------ Klasse -> Spezifikation/Template

    /** Spezifikationen (.page/.jwc), deren class-Attribut auf die Klasse zeigt. */
    public static @NotNull List<XmlFile> findSpecsForClass(@NotNull PsiClass psiClass) {
        final String fqn = psiClass.getQualifiedName();
        if (fqn == null) return List.of();
        final Project project = psiClass.getProject();
        final Map<String, List<VirtualFile>> specsByClass = ProjectCache.get(project, SPECS_BY_CLASS_KEY, () -> computeSpecsByClass(project));
        final List<XmlFile> result = new ArrayList<>();
        for (final PsiFile file : toPsiFiles(project, specsByClass.getOrDefault(fqn, List.of()))) {
            if (file instanceof final XmlFile xml) result.add(xml);
        }
        return result;
    }

    private static Map<String, List<VirtualFile>> computeSpecsByClass(Project project) {
        final Map<String, List<VirtualFile>> map = new HashMap<>();
        final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        for (final String ext : List.of(EXT_PAGE, EXT_COMPONENT)) {
            for (final VirtualFile vf : FilenameIndex.getAllFilesByExt(project, ext, scope)) {
                final XmlTag root = SpecXml.rootTag(project, vf);
                final String className = root != null ? SpecXml.attr(root, ATTR_CLASS) : null;
                if (className != null) map.computeIfAbsent(className, k -> new ArrayList<>()).add(vf);
            }
        }
        return map;
    }

    /** Templates spezifikationsloser Seiten/Komponenten, deren Paket in *-class-packages steht. */
    public static @NotNull List<PsiFile> findTemplatesByClassConvention(@NotNull PsiClass psiClass) {
        final String fqn = psiClass.getQualifiedName();
        final String name = psiClass.getName();
        if (fqn == null || name == null || !fqn.contains(".")) return List.of();
        final String pkg = StringUtil.getPackageName(fqn);
        final Project project = psiClass.getProject();
        final boolean inConventionPackage = TapestryConfiguration.getProjectMeta(project, META_PAGE_PACKAGES).contains(pkg)
                || TapestryConfiguration.getProjectMeta(project, META_COMPONENT_PACKAGES).contains(pkg);
        if (!inConventionPackage) return List.of();
        final List<VirtualFile> templates = new ArrayList<>();
        for (final String extension : TapestryConfiguration.getTemplateExtensions(project)) {
            templates.addAll(FilenameIndex.getVirtualFilesByName("%s.%s".formatted(name, extension), GlobalSearchScope.projectScope(project)));
        }
        return toPsiFiles(project, templates);
    }

    private static List<PsiFile> toPsiFiles(Project project, Collection<VirtualFile> files) {
        final PsiManager manager = PsiManager.getInstance(project);
        final List<PsiFile> result = new ArrayList<>();
        for (final VirtualFile vf : files) {
            final PsiFile psi = vf.isValid() ? manager.findFile(vf) : null;
            if (psi != null) result.add(psi);
        }
        return result;
    }
}
