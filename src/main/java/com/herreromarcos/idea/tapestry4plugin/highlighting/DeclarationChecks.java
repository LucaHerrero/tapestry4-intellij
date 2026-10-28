package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.SpecXml;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Doppelte Deklarationen, die Tapestry 4.1 beim Laden einer Spezifikation ablehnt:
 * <ul>
 *   <li>Seiten/Komponenten: Parameter (Namen und Aliase), Assets, Beans und Properties sowie eine Eigenschaft,
 *       die zwei Elemente erzeugen wollen ({@code ComponentSpecification.claimProperty})</li>
 *   <li>Application/Library: Seiten, Komponententypen, Libraries und Extensions ({@code LibrarySpecification})</li>
 * </ul>
 * Wie Tapestry wird die Datei in Dokumentreihenfolge ausgewertet; gemeldet wird jeweils die spätere Deklaration.
 * Doppelte {@code <component>}-ids meldet bereits die DTD ({@code id ID}).
 */
class DeclarationChecks {
    /** Art der Deklaration je Element (Seiten und Komponenten); der Name ist die Bezeichnung in der Meldung. */
    private static final Map<String, String> COMPONENT_KINDS = Map.of(
            TAG_PARAMETER, "Parameter",
            TAG_ASSET, "Asset", TAG_CONTEXT_ASSET, "Asset", TAG_PRIVATE_ASSET, "Asset", TAG_EXTERNAL_ASSET, "Asset",
            TAG_BEAN, "Bean",
            TAG_PROPERTY, "Property", TAG_PROPERTY_SPECIFICATION, "Property");

    /** Art der Deklaration je Element (Application und Library) und das Attribut mit dem Namen. */
    private static final Map<String, String> LIBRARY_KINDS = Map.of(
            TAG_PAGE, "Page", TAG_COMPONENT_TYPE, "Component type", TAG_LIBRARY, "Library", TAG_EXTENSION, "Extension");
    private static final Map<String, String> LIBRARY_NAME_ATTRIBUTES = Map.of(
            TAG_PAGE, ATTR_NAME, TAG_COMPONENT_TYPE, ATTR_TYPE, TAG_LIBRARY, ATTR_ID, TAG_EXTENSION, ATTR_NAME);

    private DeclarationChecks() {
    }

    static void checkAttribute(XmlAttributeValue value, AnnotationHolder holder) {
        if (!(value.getContainingFile() instanceof final XmlFile file)) return;
        final String message = problems(file).get(value);
        if (message != null) holder.newAnnotation(HighlightSeverity.ERROR, message).create();
    }

    /** Meldungen der Datei je Attributwert (Name, Alias bzw. Eigenschaft der späteren Deklaration). */
    private static Map<XmlAttributeValue, String> problems(XmlFile file) {
        return CachedValuesManager.getCachedValue(file, () -> CachedValueProvider.Result.create(compute(file), file));
    }

    private static Map<XmlAttributeValue, String> compute(XmlFile file) {
        final Map<XmlAttributeValue, String> result = new HashMap<>();
        final XmlTag root = file.getRootTag();
        if (root == null) return result;
        switch (root.getName()) {
            case ROOT_PAGE, ROOT_COMPONENT -> checkComponentSpecification(root, SpecXml.isDtd30(file), result);
            case ROOT_APPLICATION, ROOT_LIBRARY -> checkLibrarySpecification(root, result);
            default -> {
            }
        }
        return result;
    }

    private static void checkComponentSpecification(XmlTag root, boolean dtd30, Map<XmlAttributeValue, String> result) {
        final Map<String, Set<String>> declared = new HashMap<>();
        final Map<String, String> claimed = new HashMap<>();
        for (final XmlTag tag : root.getSubTags()) {
            // 3.0-DTD: <property> ist das spätere <meta>
            if (dtd30 && TAG_PROPERTY.equals(tag.getName())) continue;
            final String kind = COMPONENT_KINDS.get(tag.getName());
            if (kind != null && isDuplicate(tag, kind, declared.computeIfAbsent(kind, k -> new HashSet<>()), result)) continue;
            claimProperty(tag, SpecXml.propertyAttribute(tag, dtd30), claimed, result);
        }
    }

    private static boolean isDuplicate(XmlTag tag, String kind, Set<String> names, Map<XmlAttributeValue, String> result) {
        boolean duplicate = false;
        for (final XmlAttributeValue value : declaredNames(tag)) {
            for (final String name : names(value)) {
                if (!names.add(name)) {
                    result.put(value, "%s '%s' has already been declared".formatted(kind, name));
                    duplicate = true;
                }
            }
        }
        return duplicate;
    }

    /** Name und – bei Parametern – die Aliase; die Namen eines Parameters teilen sich einen Namensraum. */
    private static List<XmlAttributeValue> declaredNames(XmlTag tag) {
        final List<XmlAttributeValue> result = new ArrayList<>();
        addValue(tag.getAttribute(ATTR_NAME), result);
        if (TAG_PARAMETER.equals(tag.getName())) addValue(tag.getAttribute(ATTR_ALIASES), result);
        return result;
    }

    private static List<String> names(XmlAttributeValue value) {
        final XmlAttribute attribute = (XmlAttribute) value.getParent();
        return ATTR_ALIASES.equals(attribute.getName()) ? SpecXml.splitList(value.getValue()) : List.of(value.getValue().trim());
    }

    /** Die Eigenschaft, die das Element erzeugt ({@link SpecXml#propertyAttribute}), darf noch nicht vergeben sein. */
    private static void claimProperty(XmlTag tag, @Nullable XmlAttribute attribute, Map<String, String> claimed,
                                      Map<XmlAttributeValue, String> result) {
        final XmlAttributeValue value = attribute != null ? attribute.getValueElement() : null;
        if (value == null || value.getValue().isBlank()) return;
        final String property = value.getValue().trim();
        final String previous = claimed.putIfAbsent(property, tag.getName());
        if (previous != null) result.put(value, "Property '%s' is already defined by <%s>".formatted(property, previous));
    }

    private static void checkLibrarySpecification(XmlTag root, Map<XmlAttributeValue, String> result) {
        final Map<String, Set<String>> declared = new HashMap<>();
        for (final XmlTag tag : root.getSubTags()) {
            final String kind = LIBRARY_KINDS.get(tag.getName());
            if (kind == null) continue;
            final XmlAttribute attribute = tag.getAttribute(LIBRARY_NAME_ATTRIBUTES.get(tag.getName()));
            final XmlAttributeValue value = attribute != null ? attribute.getValueElement() : null;
            if (value == null || value.getValue().isBlank()) continue;
            final String name = value.getValue().trim();
            if (!declared.computeIfAbsent(kind, k -> new HashSet<>()).add(name)) {
                result.put(value, "%s '%s' has already been declared".formatted(kind, name));
            }
        }
    }

    private static void addValue(@Nullable XmlAttribute attribute, List<XmlAttributeValue> result) {
        final XmlAttributeValue value = attribute != null ? attribute.getValueElement() : null;
        if (value != null && !value.getValue().isBlank()) result.add(value);
    }
}
