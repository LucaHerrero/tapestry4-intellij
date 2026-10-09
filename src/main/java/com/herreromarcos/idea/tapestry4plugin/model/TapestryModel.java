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
    private static final Key<CachedValue<Map<String, List<String>>>> META_KEY = Key.create("tapestry4.applicationMeta");

    private TapestryModel() {
    }

    // ------------------------------------------------------------------ Kontext

    /** Kontext zu einem Template oder einer Spezifikation; für andere Dateien ein leerer Kontext. */
    public static @NotNull TapestryContext getContext(@NotNull final PsiFile anyFile) {
        final PsiFile file = anyFile.getOriginalFile();
        return CachedValuesManager.getCachedValue(file, () ->
                CachedValueProvider.Result.create(computeContext(file),
                        PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(file.getProject())));
    }

    private static TapestryContext computeContext(final PsiFile file) {
        final PsiFile template;
        final XmlFile spec;
        if (TapestryFiles.isTemplateFile(file)) {
            template = file;
            spec = findSpecForTemplate(file);
        } else if (TapestryFiles.isSpecFile(file)) {
            spec = (XmlFile) file;
            template = ContainerUtil.getFirstItem(findTemplatesForSpec(spec));
        } else {
            return new TapestryContext(null, null, null, null, SpecKind.PAGE);
        }
        final SpecKind kind = TapestryFiles.getSpecKind(spec) == SpecKind.COMPONENT ? SpecKind.COMPONENT : SpecKind.PAGE;
        final PsiFile owner = spec != null ? spec : template;
        final PsiClass declared = findDeclaredClass(spec, owner, kind);
        final PsiClass effective = declared != null ? declared
                : JavaClasses.find(kind == SpecKind.COMPONENT ? DEFAULT_COMPONENT_CLASS : DEFAULT_PAGE_CLASS, owner);
        return new TapestryContext(template, spec, declared, effective, kind);
    }

    /**
     * Klasse aus dem class-Attribut, sonst per Namenskonvention über *-class-packages der .application.
     * Ohne Spezifikation ist unklar, ob Seite oder Komponente – dann werden beide Paketlisten durchsucht.
     */
    private static @Nullable PsiClass findDeclaredClass(@Nullable XmlFile spec, @NotNull PsiFile owner, @NotNull SpecKind kind) {
        final XmlTag root = SpecXml.rootTag(spec);
        final String className = root != null ? SpecXml.attr(root, ATTR_CLASS) : null;
        if (className != null) {
            final PsiClass cls = JavaClasses.find(className, spec);
            if (cls != null) return cls;
        }
        final String simpleName = TapestryFiles.baseName(owner);
        if (simpleName == null) return null;
        final List<String> metaKeys = spec == null ? List.of(META_PAGE_PACKAGES, META_COMPONENT_PACKAGES)
                : List.of(kind == SpecKind.COMPONENT ? META_COMPONENT_PACKAGES : META_PAGE_PACKAGES);
        final Map<String, List<String>> meta = getApplicationMeta(owner.getProject());
        for (final String metaKey : metaKeys) {
            for (final String pkg : meta.getOrDefault(metaKey, List.of())) {
                final PsiClass cls = JavaClasses.find("%s.%s".formatted(pkg, simpleName), owner);
                if (cls != null) return cls;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ Template <-> Spezifikation

    /** Spezifikation gleichen Namens (.page oder .jwc) mit dem ähnlichsten Pfad. */
    public static @Nullable XmlFile findSpecForTemplate(@NotNull PsiFile template) {
        final VirtualFile vf = template.getOriginalFile().getVirtualFile();
        if (vf == null) return null;
        final Project project = template.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final List<VirtualFile> candidates = new ArrayList<>();
        for (final String ext : List.of(EXT_PAGE, EXT_COMPONENT)) {
            candidates.addAll(FilenameIndex.getVirtualFilesByName("%s.%s".formatted(vf.getNameWithoutExtension(), ext), scope));
        }
        final VirtualFile closest = TapestryPaths.closest(vf, candidates);
        return closest != null && PsiManager.getInstance(project).findFile(closest) instanceof final XmlFile xml ? xml : null;
    }

    /** Templates gleichen Namens mit dem ähnlichsten Pfad (bei Gleichstand alle). */
    public static @NotNull List<PsiFile> findTemplatesForSpec(@NotNull XmlFile spec) {
        final VirtualFile vf = spec.getOriginalFile().getVirtualFile();
        if (vf == null) return List.of();
        final Collection<VirtualFile> candidates = FilenameIndex.getVirtualFilesByName(
                "%s.%s".formatted(vf.getNameWithoutExtension(), TEMPLATE_EXT), GlobalSearchScope.allScope(spec.getProject()));
        return toPsiFiles(spec.getProject(), TapestryPaths.allClosest(vf, candidates));
    }

    // ------------------------------------------------------------------ Klasse -> Spezifikation/Template

    /** Spezifikationen (.page/.jwc), deren class-Attribut auf die Klasse zeigt. */
    public static @NotNull List<XmlFile> findSpecsForClass(@NotNull final PsiClass psiClass) {
        final String fqn = psiClass.getQualifiedName();
        if (fqn == null) return List.of();
        final Project project = psiClass.getProject();
        final Map<String, List<VirtualFile>> specsByClass = CachedValuesManager.getManager(project).getCachedValue(project, SPECS_BY_CLASS_KEY, () ->
                CachedValueProvider.Result.create(computeSpecsByClass(project), PsiModificationTracker.MODIFICATION_COUNT), false);
        final List<XmlFile> result = new ArrayList<>();
        for (final PsiFile file : toPsiFiles(project, specsByClass.getOrDefault(fqn, List.of()))) {
            if (file instanceof final XmlFile xml) result.add(xml);
        }
        return result;
    }

    private static Map<String, List<VirtualFile>> computeSpecsByClass(final Project project) {
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
    public static @NotNull List<PsiFile> findTemplatesByClassConvention(@NotNull final PsiClass psiClass) {
        final String fqn = psiClass.getQualifiedName();
        final String name = psiClass.getName();
        if (fqn == null || name == null || !fqn.contains(".")) return List.of();
        final String pkg = StringUtil.getPackageName(fqn);
        final Map<String, List<String>> meta = getApplicationMeta(psiClass.getProject());
        final boolean inConventionPackage = meta.getOrDefault(META_PAGE_PACKAGES, List.of()).contains(pkg)
                || meta.getOrDefault(META_COMPONENT_PACKAGES, List.of()).contains(pkg);
        if (!inConventionPackage) return List.of();
        final Project project = psiClass.getProject();
        return toPsiFiles(project, FilenameIndex.getVirtualFilesByName("%s.%s".formatted(name, TEMPLATE_EXT), GlobalSearchScope.projectScope(project)));
    }

    // ------------------------------------------------------------------ Meta-Daten der .application

    /** meta-Werte aller .application-Dateien (z.B. org.apache.tapestry.page-class-packages), kommasepariert aufgeteilt. */
    public static @NotNull Map<String, List<String>> getApplicationMeta(@NotNull final Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, META_KEY, () ->
                CachedValueProvider.Result.create(computeApplicationMeta(project), PsiModificationTracker.MODIFICATION_COUNT), false);
    }

    private static Map<String, List<String>> computeApplicationMeta(final Project project) {
        final Map<String, List<String>> meta = new HashMap<>();
        for (final VirtualFile app : FilenameIndex.getAllFilesByExt(project, EXT_APPLICATION, GlobalSearchScope.projectScope(project))) {
            final XmlTag root = SpecXml.rootTag(project, app);
            if (root == null) continue;
            for (final XmlTag tag : root.findSubTags(TAG_META)) {
                final String key = SpecXml.attr(tag, "key");
                String value = tag.getAttributeValue(ATTR_VALUE);
                if (value == null) value = tag.getValue().getTrimmedText();
                if (key != null) meta.computeIfAbsent(key, k -> new ArrayList<>()).addAll(SpecXml.splitList(value));
            }
        }
        return meta;
    }

    private static List<PsiFile> toPsiFiles(final Project project, final Collection<VirtualFile> files) {
        final PsiManager manager = PsiManager.getInstance(project);
        final List<PsiFile> result = new ArrayList<>();
        for (final VirtualFile vf : files) {
            final PsiFile psi = vf.isValid() ? manager.findFile(vf) : null;
            if (psi != null) result.add(psi);
        }
        return result;
    }
}
