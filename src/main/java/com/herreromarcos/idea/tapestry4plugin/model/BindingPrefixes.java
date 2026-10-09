package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Registrierte Binding-Präfixe: Tapestry ordnet ein Präfix über die Configuration
 * {@code tapestry.bindings.BindingFactories} ({@code <binding prefix="..." service-id="..."/>}) einer BindingFactory zu.
 * Neben den dokumentierten Präfixen können Projekte und Bibliotheken dort eigene beitragen.
 */
public class BindingPrefixes {
    private static final String BINDING_FACTORIES = "tapestry.bindings.BindingFactories";
    private static final String TAG_BINDING = "binding";
    private static final String ATTR_PREFIX = "prefix";

    private BindingPrefixes() {
    }

    /** Präfix → {@code prefix}-Attributwert der Registrierung (dokumentierte und projekteigene). */
    public static @NotNull Map<String, XmlAttributeValue> getDeclarations(@NotNull final Project project) {
        if (DumbService.isDumb(project)) return Map.of();
        final Map<String, XmlAttributeValue> result = new LinkedHashMap<>();
        for (final XmlTag binding : HiveModules.getContributedElements(project, BINDING_FACTORIES, TAG_BINDING)) {
            final XmlAttribute prefix = binding.getAttribute(ATTR_PREFIX);
            if (prefix != null && prefix.getValueElement() != null && !StringUtil.isEmptyOrSpaces(prefix.getValue())) {
                result.putIfAbsent(prefix.getValue().trim(), prefix.getValueElement());
            }
        }
        return result;
    }

    /** Projekteigene, nicht dokumentierte Präfixe; ohne Index (Dumb Mode) leer. */
    public static @NotNull Set<String> getCustom(@NotNull final Project project) {
        final Set<String> result = new LinkedHashSet<>(getDeclarations(project).keySet());
        TapestryConstants.BINDING_PREFIXES.forEach(result::remove);
        return result;
    }

    /** Sind die Registrierungen bekannt (Moduldeskriptoren im Classpath), sodass unbekannte Präfixe gemeldet werden können? */
    public static boolean isRegistryKnown(@NotNull final Project project) {
        return !DumbService.isDumb(project) && !getDeclarations(project).isEmpty();
    }
}
