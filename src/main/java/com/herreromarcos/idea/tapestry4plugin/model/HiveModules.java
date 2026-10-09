package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.ATTR_ID;
import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.HIVEMODULE_XML;

/**
 * Beiträge ({@code <contribution configuration-id>}) aller HiveMind-Moduldeskriptoren: jede {@code hivemodule.xml}
 * im Projekt und in den Bibliotheken samt ihrer {@code <sub-module>}-Deskriptoren. Die Configuration-IDs werden
 * voll qualifiziert ("FactoryObjects" im Modul tapestry.state → "tapestry.state.FactoryObjects").
 *
 * <p>Das Plugin liest daraus nur, was Tapestry selbst auswertet (State Objects, Binding-Präfixe, Symbole); die
 * eigentliche HiveMind-Unterstützung (Services, Configurations) liefert das HiveMind-Plugin.
 */
public class HiveModules {
    private static final Key<CachedValue<Map<String, List<XmlTag>>>> CONTRIBUTIONS_KEY = Key.create("tapestry4.hiveContributions");
    private static final String TAG_MODULE = "module";
    private static final String TAG_SUB_MODULE = "sub-module";
    private static final String TAG_CONTRIBUTION = "contribution";

    private HiveModules() {
    }

    /** Die Kind-Elemente aller Beiträge zur Configuration (voll qualifizierte ID), in Fundreihenfolge. */
    public static @NotNull List<XmlTag> getContributedElements(@NotNull final Project project, @NotNull final String configurationId,
                                                               @NotNull final String elementName) {
        final List<XmlTag> result = new ArrayList<>();
        for (final XmlTag contribution : getContributions(project).getOrDefault(configurationId, List.of())) {
            result.addAll(List.of(contribution.findSubTags(elementName)));
        }
        return result;
    }

    private static Map<String, List<XmlTag>> getContributions(final Project project) {
        return CachedValuesManager.getManager(project).getCachedValue(project, CONTRIBUTIONS_KEY, () ->
                CachedValueProvider.Result.create(compute(project),
                        PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(project)), false);
    }

    private static Map<String, List<XmlTag>> compute(final Project project) {
        final Map<String, List<XmlTag>> result = new LinkedHashMap<>();
        final Deque<VirtualFile> queue = new ArrayDeque<>(
                FilenameIndex.getVirtualFilesByName(HIVEMODULE_XML, GlobalSearchScope.allScope(project)));
        final Set<VirtualFile> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            final VirtualFile descriptor = queue.poll();
            if (!visited.add(descriptor)) continue;
            final XmlTag module = SpecXml.rootTag(project, descriptor);
            if (module == null || !TAG_MODULE.equals(module.getName())) continue;
            final String moduleId = StringUtil.notNullize(SpecXml.attr(module, ATTR_ID));
            for (final XmlTag tag : module.getSubTags()) {
                if (TAG_SUB_MODULE.equals(tag.getName())) {
                    final VirtualFile subModule = findSubModule(descriptor, tag);
                    if (subModule != null) queue.add(subModule);
                } else if (TAG_CONTRIBUTION.equals(tag.getName())) {
                    result.computeIfAbsent(qualifiedConfigurationId(moduleId, tag), k -> new ArrayList<>()).add(tag);
                }
            }
        }
        return result;
    }

    private static @Nullable VirtualFile findSubModule(final VirtualFile descriptor, final XmlTag subModule) {
        final String path = SpecXml.attr(subModule, "descriptor");
        return path != null && descriptor.getParent() != null ? descriptor.getParent().findFileByRelativePath(path) : null;
    }

    /** Ohne Punkt ist die Configuration-ID relativ zum Modul. */
    private static String qualifiedConfigurationId(final String moduleId, final XmlTag contribution) {
        final String configuration = StringUtil.notNullize(SpecXml.attr(contribution, "configuration-id"));
        return configuration.contains(".") ? configuration : "%s.%s".formatted(moduleId, configuration);
    }
}
