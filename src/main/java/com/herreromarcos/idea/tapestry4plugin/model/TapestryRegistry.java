package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Projektweites Verzeichnis aller Komponententypen und Seiten (gecacht bis zur nächsten PSI-/Root-Änderung).
 *
 * <p>Komponententypen stammen aus (in dieser Priorität):
 * <ol>
 *   <li>{@code <component-type>} der .application/.library-Dateien des Projekts</li>
 *   <li>losen .jwc-Dateien im Projekt</li>
 *   <li>{@code org/apache/tapestry/Framework.library} aus dem Classpath (zusätzlich als "framework:Typ")</li>
 *   <li>eingebundenen Bibliotheken {@code <library id="contrib" ...>} mit Präfix ("contrib:Table")</li>
 * </ol>
 */
public class TapestryRegistry {
    private static final Key<CachedValue<Map<String, VirtualFile>>> COMPONENT_TYPES_KEY = Key.create("tapestry4.componentTypes");
    private static final Key<CachedValue<Map<String, VirtualFile>>> PAGES_KEY = Key.create("tapestry4.pages");
    private static final Key<CachedValue<Map<VirtualFile, VirtualFile>>> LIBRARY_MEMBERS_KEY = Key.create("tapestry4.libraryMembers");
    private static final Key<CachedValue<Boolean>> FRAMEWORK_PRESENT_KEY = Key.create("tapestry4.frameworkPresent");

    private TapestryRegistry() {
    }

    // ------------------------------------------------------------------ Komponententypen

    public static @NotNull Map<String, VirtualFile> getComponentTypes(@NotNull Project project) {
        return ProjectCache.get(project, COMPONENT_TYPES_KEY, () -> computeComponentTypes(project));
    }

    /** Alle Typnamen für die Completion; ohne Tapestry-JAR im Classpath ergänzt um die bekannten Framework-Komponenten. */
    public static @NotNull Collection<String> getAllTypeNames(@NotNull Project project) {
        final Set<String> names = new LinkedHashSet<>(getComponentTypes(project).keySet());
        if (!isFrameworkPresent(project)) names.addAll(FRAMEWORK_COMPONENTS);
        return names;
    }

    /** Löst "Insert", "contrib:Table" oder eine Pfadangabe wie "common/Border" auf die .jwc-Datei auf. */
    public static @Nullable XmlFile resolveComponentType(@NotNull String typeName, @NotNull PsiElement context) {
        final String type = typeName.trim();
        if (type.isEmpty()) return null;
        final Project project = context.getProject();
        final Map<String, VirtualFile> types = getComponentTypes(project);
        final VirtualFile registered = types.get(type);
        final VirtualFile vf = registered == null && type.contains("/") && !type.contains(":")
                ? resolveTypePath(type, context, types) : registered;
        final PsiFile psi = vf != null && vf.isValid() ? PsiManager.getInstance(project).findFile(vf) : null;
        return psi instanceof final XmlFile xml ? xml : null;
    }

    private static @Nullable VirtualFile resolveTypePath(String type, PsiElement context, Map<String, VirtualFile> types) {
        final String fileSuffix = "%s.%s".formatted(type, EXT_COMPONENT);
        final VirtualFile base = context.getContainingFile().getOriginalFile().getVirtualFile();
        final VirtualFile relative = base != null && base.getParent() != null ? base.getParent().findFileByRelativePath(fileSuffix) : null;
        if (relative != null) return relative;
        final VirtualFile candidate = types.get(StringUtil.substringAfterLast(type, "/"));
        return candidate != null && candidate.getPath().endsWith(fileSuffix) ? candidate : null;
    }

    /** Liegt die Tapestry-Framework-Bibliothek im Classpath? Sonst werden Framework-Komponenten nicht als Fehler markiert. */
    public static boolean isFrameworkPresent(@NotNull Project project) {
        return ProjectCache.get(project, FRAMEWORK_PRESENT_KEY, () -> !findFrameworkLibraries(project).isEmpty());
    }

    /** Typen mit Seitenlink-Semantik (Parameter "page" ist ein Seitenname). */
    public static boolean isPageLink(@Nullable String type) {
        return type != null && type.endsWith(PAGE_LINK);
    }

    private static List<VirtualFile> findFrameworkLibraries(Project project) {
        return FilenameIndex.getVirtualFilesByName(FRAMEWORK_LIBRARY, GlobalSearchScope.allScope(project)).stream()
                .filter(vf -> vf.getPath().contains("org/apache/tapestry/"))
                .toList();
    }

    private static Map<String, VirtualFile> computeComponentTypes(Project project) {
        final Map<String, VirtualFile> unprefixed = new LinkedHashMap<>();
        final Map<String, VirtualFile> prefixed = new LinkedHashMap<>();
        final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);

        // 1. <component-type> aus .application/.library des Projekts
        for (final String ext : List.of(EXT_APPLICATION, EXT_LIBRARY)) {
            for (final VirtualFile spec : FilenameIndex.getAllFilesByExt(project, ext, projectScope)) {
                collectComponentTypes(project, spec, null, unprefixed);
            }
        }
        // eingebundene Bibliotheken: ihre Typen bekommen das Präfix der library id
        findReferencedLibraries(project).forEach((library, id) -> {
            collectComponentTypes(project, library, id, prefixed);
            // "In the same package folder as the library specification" (User's Guide, Library component search path)
            for (final VirtualFile child : siblings(library)) {
                if (EXT_COMPONENT.equals(child.getExtension())) {
                    prefixed.putIfAbsent("%s:%s".formatted(id, child.getNameWithoutExtension()), child);
                }
            }
        });

        // 2. Lose .jwc-Dateien im Projekt (WEB-INF, Kontext-Root, Pakete)
        for (final VirtualFile jwc : FilenameIndex.getAllFilesByExt(project, EXT_COMPONENT, projectScope)) {
            unprefixed.putIfAbsent(jwc.getNameWithoutExtension(), jwc);
        }

        // 3. Framework-Komponenten, auch als "framework:Insert"
        for (final VirtualFile framework : findFrameworkLibraries(project)) {
            final Map<String, VirtualFile> types = new LinkedHashMap<>();
            collectComponentTypes(project, framework, null, types);
            types.forEach(unprefixed::putIfAbsent);
            types.forEach((type, file) -> prefixed.putIfAbsent("%s:%s".formatted(FRAMEWORK_NAMESPACE, type), file));
        }

        final Map<String, VirtualFile> result = new LinkedHashMap<>(unprefixed);
        result.putAll(prefixed);
        return result;
    }

    /** Über {@code <library id specification-path>} eingebundene Bibliotheken (auch verschachtelte) → library id. */
    private static Map<VirtualFile, String> findReferencedLibraries(Project project) {
        final Map<VirtualFile, String> libraries = new LinkedHashMap<>();
        final GlobalSearchScope projectScope = GlobalSearchScope.projectScope(project);
        for (final String ext : List.of(EXT_APPLICATION, EXT_LIBRARY)) {
            for (final VirtualFile spec : FilenameIndex.getAllFilesByExt(project, ext, projectScope)) {
                collectLibraries(project, spec, libraries);
            }
        }
        final Deque<VirtualFile> queue = new ArrayDeque<>(libraries.keySet());
        final Set<VirtualFile> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            final VirtualFile library = queue.poll();
            if (!visited.add(library)) continue;
            final Map<VirtualFile, String> nested = new LinkedHashMap<>();
            collectLibraries(project, library, nested);
            nested.forEach((file, id) -> {
                libraries.putIfAbsent(file, id);
                queue.add(file);
            });
        }
        return libraries;
    }

    private static List<VirtualFile> siblings(VirtualFile file) {
        final VirtualFile folder = file.getParent();
        return folder != null ? List.of(folder.getChildren()) : List.of();
    }

    // ------------------------------------------------------------------ Bibliotheken als Namespace

    /**
     * Bibliothek (.library), zu deren Namespace eine Spezifikation oder ein Template gehört, oder {@code null} für den
     * Application-Namespace. Zur Bibliothek gehören die über {@code <component-type>}/{@code <page>} eingetragenen
     * Spezifikationen und die Dateien im Ordner der Bibliotheksspezifikation (sofern dort keine .application liegt).
     */
    public static @Nullable VirtualFile findLibrary(@NotNull Project project, @NotNull VirtualFile file) {
        final Map<VirtualFile, VirtualFile> members = ProjectCache.get(project, LIBRARY_MEMBERS_KEY, () -> computeLibraryMembers(project));
        final VirtualFile library = members.get(file);
        if (library != null) return library;
        return file.getParent() != null ? members.get(file.getParent()) : null;
    }

    /** Mitglied (Spezifikation oder Ordner) → Bibliothek. */
    private static Map<VirtualFile, VirtualFile> computeLibraryMembers(Project project) {
        final Set<VirtualFile> libraries = new LinkedHashSet<>(findReferencedLibraries(project).keySet());
        libraries.addAll(FilenameIndex.getAllFilesByExt(project, EXT_LIBRARY, GlobalSearchScope.projectScope(project)));
        libraries.addAll(findFrameworkLibraries(project));
        final Map<VirtualFile, VirtualFile> members = new HashMap<>();
        for (final VirtualFile library : libraries) {
            forEachPathEntry(project, library, TAG_COMPONENT_TYPE, ATTR_TYPE, (type, target) -> members.putIfAbsent(target, library));
            forEachPathEntry(project, library, TAG_PAGE, ATTR_NAME, (name, target) -> members.putIfAbsent(target, library));
            final boolean sharedWithApplication = siblings(library).stream().anyMatch(f -> EXT_APPLICATION.equals(f.getExtension()));
            if (library.getParent() != null && !sharedWithApplication) members.putIfAbsent(library.getParent(), library);
        }
        return members;
    }

    private static void collectComponentTypes(Project project, VirtualFile specFile, @Nullable String prefix, Map<String, VirtualFile> out) {
        forEachPathEntry(project, specFile, TAG_COMPONENT_TYPE, ATTR_TYPE,
                (type, target) -> out.put(prefix == null ? type : "%s:%s".formatted(prefix, type), target));
    }

    private static void collectLibraries(Project project, VirtualFile specFile, Map<VirtualFile, String> out) {
        forEachPathEntry(project, specFile, TAG_LIBRARY, ATTR_ID, (id, target) -> out.putIfAbsent(target, id));
    }

    /** Für {@code <tag key="..." specification-path="...">}: Schlüssel und aufgelöste Zieldatei. */
    private static void forEachPathEntry(Project project, VirtualFile specFile, String tagName, String keyAttribute,
                                         PathEntryConsumer consumer) {
        final XmlTag root = SpecXml.rootTag(project, specFile);
        if (root == null) return;
        for (final XmlTag tag : root.findSubTags(tagName)) {
            final String key = SpecXml.attr(tag, keyAttribute);
            final String path = SpecXml.attr(tag, ATTR_SPECIFICATION_PATH);
            final VirtualFile target = key != null && path != null ? TapestryPaths.resolve(project, path, specFile) : null;
            if (target != null) consumer.accept(key, target);
        }
    }

    @FunctionalInterface
    private interface PathEntryConsumer {
        void accept(String key, VirtualFile target);
    }

    // ------------------------------------------------------------------ Seiten

    /** Seitenname → .page (oder Template einer spezifikationslosen Seite). */
    public static @NotNull Map<String, VirtualFile> getPages(@NotNull Project project) {
        return ProjectCache.get(project, PAGES_KEY, () -> computePages(project));
    }

    public static @Nullable PsiFile resolvePage(@NotNull String name, @NotNull PsiElement context) {
        final VirtualFile vf = getPages(context.getProject()).get(name.trim());
        return vf != null && vf.isValid() ? PsiManager.getInstance(context.getProject()).findFile(vf) : null;
    }

    private static Map<String, VirtualFile> computePages(Project project) {
        final Map<String, VirtualFile> result = new LinkedHashMap<>();
        final GlobalSearchScope scope = GlobalSearchScope.projectScope(project);
        // 1. <page name="..." specification-path="..."> der .application
        for (final VirtualFile app : FilenameIndex.getAllFilesByExt(project, EXT_APPLICATION, scope)) {
            forEachPathEntry(project, app, TAG_PAGE, ATTR_NAME, result::put);
        }
        // 2. .page-Dateien: einfacher Name und Pfad unterhalb von WEB-INF ("admin/Users")
        for (final VirtualFile page : FilenameIndex.getAllFilesByExt(project, EXT_PAGE, scope)) {
            result.putIfAbsent(page.getNameWithoutExtension(), page);
            final String belowWebInf = TapestryPaths.pathBelowWebInf(page);
            if (belowWebInf != null) result.putIfAbsent(belowWebInf, page);
        }
        // 3. Spezifikationslose Seiten: Templates im Kontext-Root (nicht unter WEB-INF)
        for (final String extension : TapestryConfiguration.getTemplateExtensions(project)) {
            for (final VirtualFile template : FilenameIndex.getAllFilesByExt(project, extension, scope)) {
                final VirtualFile webRoot = TapestryPaths.webRoot(template);
                final String relative = webRoot != null ? VfsUtilCore.getRelativePath(template, webRoot, '/') : null;
                if (relative != null && !relative.startsWith(WEB_INF + "/")) {
                    result.putIfAbsent(StringUtil.trimEnd(relative, "." + extension), template);
                }
            }
        }
        return result;
    }
}
