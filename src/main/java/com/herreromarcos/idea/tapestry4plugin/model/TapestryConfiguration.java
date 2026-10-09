package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Konfiguration über Meta-Daten, gesucht wie in Tapestrys {@code tapestry.props.ComponentPropertySource}:
 * <ol>
 *   <li>{@code <meta>} der Spezifikation bzw. {@code @Meta} der Klasse (Unterklassen überschreiben Basisklassen)</li>
 *   <li>{@code <meta>} des Namespace: die .library, zu der die Seite/Komponente gehört, sonst die .application</li>
 *   <li>global: {@code <init-param>} des Servlets, {@code <context-param>} (web.xml), dann die Symbole aus
 *       {@code hivemind.ApplicationDefaults}. JVM-System-Properties ({@code -Dorg.apache.tapestry...}) gibt es nur
 *       zur Laufzeit und bleiben unberücksichtigt; {@code hivemind.FactoryDefaults} enthält Tapestrys eigene
 *       Standardwerte, die das Plugin ohnehin als Fallback verwendet.</li>
 * </ol>
 * Daraus abgeleitet: Standard-Binding-Präfix, Name des jwcid-Attributs, Template-Endung, Klassenpakete.
 */
public class TapestryConfiguration {
    private static final Key<CachedValue<Map<String, List<String>>>> META_KEY = Key.create("tapestry4.applicationMeta");
    private static final Key<CachedValue<Map<String, List<String>>>> GLOBAL_KEY = Key.create("tapestry4.globalProperties");
    private static final String APPLICATION_DEFAULTS = "hivemind.ApplicationDefaults";

    private TapestryConfiguration() {
    }

    // ------------------------------------------------------------------ Meta-Daten

    /** Meta-Wert für eine Seite/Komponente über den vollständigen Suchpfad (siehe Klassenbeschreibung). */
    public static @Nullable String findMeta(@NotNull final TapestryContext ctx, @NotNull final String key) {
        return ContainerUtil.getFirstItem(findMetaList(ctx, key));
    }

    /** Wie {@link #findMeta}, aber als kommaseparierte Liste (z.B. Paketlisten). */
    public static @NotNull List<String> findMetaList(@NotNull final TapestryContext ctx, @NotNull final String key) {
        final String specMeta = SpecXml.meta(SpecXml.rootTag(ctx.spec()), key);
        if (specMeta != null) return SpecXml.splitList(specMeta);
        for (PsiClass cls = ctx.declaredClass(); cls != null; cls = cls.getSuperClass()) {
            final String classMeta = JavaClasses.metaAnnotationValue(cls, key);
            if (classMeta != null) return SpecXml.splitList(classMeta);
        }
        final PsiFile owner = ctx.spec() != null ? ctx.spec() : ctx.template();
        return owner != null ? getNamespaceMeta(owner, key) : List.of();
    }

    /** Meta-Wert mit Fallback, falls nicht oder leer konfiguriert. */
    private static @NotNull String findMetaOrDefault(@NotNull final TapestryContext ctx, @NotNull final String key,
                                                     @NotNull final String defaultValue) {
        final String configured = findMeta(ctx, key);
        return configured != null && !configured.isBlank() ? configured.trim() : defaultValue;
    }

    /**
     * Namespace-Wert für die Datei einer Seite/Komponente: {@code <meta>} ihrer .library bzw. der .application,
     * sonst der globale Wert. Ohne Zugriff auf Klasse oder Kontext, daher auch während der Kontextberechnung nutzbar.
     */
    public static @NotNull List<String> getNamespaceMeta(@NotNull final PsiFile owner, @NotNull final String key) {
        final Project project = owner.getProject();
        final VirtualFile vf = owner.getOriginalFile().getVirtualFile();
        final VirtualFile library = vf != null ? TapestryRegistry.findLibrary(project, vf) : null;
        final List<String> namespaceValues = library != null ? readMeta(project, library).getOrDefault(key, List.of())
                : getApplicationMeta(project).getOrDefault(key, List.of());
        return !namespaceValues.isEmpty() ? namespaceValues : getGlobalProperties(project).getOrDefault(key, List.of());
    }

    /** Projektweiter Wert ohne Bezug zu einer Seite: .application, sonst global. */
    public static @NotNull List<String> getProjectMeta(@NotNull final Project project, @NotNull final String key) {
        final List<String> applicationValues = getApplicationMeta(project).getOrDefault(key, List.of());
        return !applicationValues.isEmpty() ? applicationValues : getGlobalProperties(project).getOrDefault(key, List.of());
    }

    /** meta-Werte aller .application-Dateien (z.B. org.apache.tapestry.page-class-packages), kommasepariert aufgeteilt. */
    private static Map<String, List<String>> getApplicationMeta(final Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, META_KEY, () -> {
            final Map<String, List<String>> meta = new HashMap<>();
            for (final VirtualFile app : FilenameIndex.getAllFilesByExt(project, EXT_APPLICATION, GlobalSearchScope.projectScope(project))) {
                readMeta(project, app).forEach((key, values) -> meta.computeIfAbsent(key, k -> new ArrayList<>()).addAll(values));
            }
            return CachedValueProvider.Result.create(meta, PsiModificationTracker.MODIFICATION_COUNT);
        }, false);
    }

    /** {@code <meta key value>} bzw. {@code <meta key>Wert</meta>} einer .application/.library. */
    private static Map<String, List<String>> readMeta(final Project project, final VirtualFile specFile) {
        final Map<String, List<String>> meta = new HashMap<>();
        final XmlTag root = SpecXml.rootTag(project, specFile);
        if (root == null) return meta;
        for (final XmlTag tag : root.findSubTags(TAG_META)) {
            final String key = SpecXml.attr(tag, "key");
            final String value = tag.getAttributeValue(ATTR_VALUE);
            if (key != null) {
                meta.computeIfAbsent(key, k -> new ArrayList<>())
                        .addAll(SpecXml.splitList(value != null ? value : tag.getValue().getTrimmedText()));
            }
        }
        return meta;
    }

    /**
     * Globale Werte (tapestry.props.GlobalPropertySources): Servlet-{@code <init-param>}, {@code <context-param>},
     * dann die Symbole aus {@code hivemind.ApplicationDefaults}.
     */
    private static Map<String, List<String>> getGlobalProperties(final Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, GLOBAL_KEY, () -> {
            final Map<String, List<String>> result = new LinkedHashMap<>();
            WebXml.tapestryParameters(project).forEach((key, value) -> result.putIfAbsent(key, SpecXml.splitList(value)));
            for (final XmlTag symbol : HiveModules.getContributedElements(project, APPLICATION_DEFAULTS, "default")) {
                final String key = SpecXml.attr(symbol, "symbol");
                final String value = symbol.getAttributeValue(ATTR_VALUE);
                if (key != null && value != null) result.putIfAbsent(key, SpecXml.splitList(value));
            }
            return CachedValueProvider.Result.create(result,
                    PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(project));
        }, false);
    }

    // ------------------------------------------------------------------ Binding-Präfix

    /**
     * Standard-Präfix für Bindings ohne Präfix in Spezifikationen und Annotationen: "ognl", sofern nicht über
     * {@code org.apache.tapestry.default-binding-prefix} anders konfiguriert.
     */
    public static @NotNull String getDefaultBindingPrefix(@NotNull final PsiFile specification) {
        return findMetaOrDefault(TapestryModel.getContext(specification), META_DEFAULT_BINDING_PREFIX, PREFIX_OGNL);
    }

    // ------------------------------------------------------------------ jwcid-Attribut

    /**
     * Name des Komponenten-Attributs im Template: "jwcid", sofern nicht über
     * {@code org.apache.tapestry.jwcid-attribute-name} (Spezifikation, {@code @Meta}, .application) geändert.
     * Während der Indexierung wird ohne Index-Zugriff der Standard verwendet.
     */
    public static @NotNull String getJwcidAttribute(@NotNull final PsiFile template) {
        final PsiFile file = template.getOriginalFile();
        if (DumbService.isDumb(file.getProject())) return JWCID;
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(
                findMetaOrDefault(TapestryModel.getContext(file), META_JWCID_ATTRIBUTE, JWCID),
                PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(file.getProject())));
    }

    /** Das Komponenten-Attribut ({@code jwcid} bzw. konfigurierter Name) eines Template-Tags. */
    public static @Nullable XmlAttribute findJwcidAttribute(@NotNull final XmlTag tag) {
        return tag.getAttribute(getJwcidAttribute(tag.getContainingFile()));
    }

    /** Wert-Element des Komponenten-Attributs, {@code null} ohne Attribut oder Wert. */
    public static @Nullable XmlAttributeValue findJwcidValueElement(@NotNull final XmlTag tag) {
        final XmlAttribute attribute = findJwcidAttribute(tag);
        return attribute != null ? attribute.getValueElement() : null;
    }

    /** Wert des Komponenten-Attributs, {@code null} ohne Attribut oder Wert. */
    public static @Nullable String findJwcidValue(@NotNull final XmlTag tag) {
        final XmlAttribute attribute = findJwcidAttribute(tag);
        return attribute != null ? attribute.getValue() : null;
    }

    /** Ist das Tag eine Tapestry-Komponente (trägt es das Komponenten-Attribut)? */
    public static boolean isComponentTag(@NotNull final XmlTag tag) {
        return findJwcidAttribute(tag) != null;
    }

    public static boolean isJwcidAttribute(@NotNull final XmlAttribute attribute) {
        return attribute.getName().equalsIgnoreCase(getJwcidAttribute(attribute.getContainingFile()));
    }

    // ------------------------------------------------------------------ Template-Endung

    /**
     * Template-Endung einer Spezifikation: {@code org.apache.tapestry.template-extension} aus der Spezifikation,
     * sonst aus dem Namespace bzw. global, sonst "html". {@code @Meta} der Klasse bleibt hier außen vor, weil die
     * Klasse erst über den Kontext (und damit über das Template) bestimmt wird.
     */
    public static @NotNull String getTemplateExtension(@NotNull final XmlFile spec) {
        final String specMeta = SpecXml.meta(spec.getRootTag(), META_TEMPLATE_EXTENSION);
        if (specMeta != null && !specMeta.isBlank()) return specMeta.trim();
        final List<String> configured = getNamespaceMeta(spec, META_TEMPLATE_EXTENSION);
        return configured.isEmpty() ? TEMPLATE_EXT : configured.get(0);
    }

    /** Alle im Projekt verwendeten Template-Endungen: "html" plus konfigurierte. */
    public static @NotNull Set<String> getTemplateExtensions(@NotNull final Project project) {
        final Set<String> result = new LinkedHashSet<>();
        result.add(TEMPLATE_EXT);
        result.addAll(getProjectMeta(project, META_TEMPLATE_EXTENSION));
        return result;
    }
}
