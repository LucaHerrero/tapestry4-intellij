package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.herreromarcos.idea.tapestry4plugin.model.SpecXml;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryRegistry;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Prüfungen in Spezifikationen, wie sie Tapestry 4.1 beim Parsen ({@code SpecificationParser}) und Laden
 * ({@code PageLoader}) vornimmt: id- und Namens-Syntax, {@code <component>} mit Typ oder copy-of, reservierte
 * Library-ids, Pflichtparameter ohne Default, {@code inherit-informal-parameters}, doppelt gebundene Parameter,
 * veraltete Komponenten und Parameter, Aliase, reservierte Namen. Doppelte Deklarationen prüft
 * {@link DeclarationChecks}.
 */
class SpecificationChecks {
    /** Asset-Name, der das Template der Seite/Komponente bestimmt. */
    private static final String TEMPLATE_ASSET = "$template";
    /** Library-ids, die Tapestry für eigene Namespaces reserviert ({@code SpecificationParser.enterLibrary}). */
    private static final Set<String> RESERVED_LIBRARY_IDS = Set.of(FRAMEWORK_NAMESPACE, APPLICATION_NAMESPACE);

    // Muster aus Tapestrys SpecificationParser
    private static final String ID = ComponentUsageChecks.IDENTIFIER.pattern();
    private static final String EXTENDED_ID = "_?[a-zA-Z][\\w-]*";
    private static final Pattern PAGE_NAME = Pattern.compile("%s(/%s)*".formatted(ID, EXTENDED_ID));
    private static final Pattern COMPONENT_ALIAS = Pattern.compile("(%s/)*%s".formatted(ID, ID));
    private static final Pattern COMPONENT_TYPE = Pattern.compile("(%s:)?(%s/)*%s".formatted(ID, ID, ID));
    private static final Pattern EXTENSION_NAME = Pattern.compile("%s(\\.%s)*".formatted(EXTENDED_ID, EXTENDED_ID));

    private record NameRule(Pattern pattern, String message) {
    }

    private static final NameRule PARAMETER_NAME = identifier("Invalid parameter name '%s': must be a Java identifier");
    private static final NameRule PROPERTY_NAME = identifier("Invalid property name '%s': must be a Java identifier");

    /** Namensregeln je {@code element@attribut}; die ids von {@code <component>} prüft {@link ComponentUsageChecks}. */
    private static final Map<String, NameRule> NAME_RULES = Map.ofEntries(
            Map.entry("parameter@name", PARAMETER_NAME),
            Map.entry("binding@name", PARAMETER_NAME),
            Map.entry("bean@name", identifier("Invalid bean name '%s': must be a Java identifier")),
            Map.entry("asset@name", identifier("Invalid asset name '%s': must be a Java identifier")),
            Map.entry("property@name", PROPERTY_NAME),
            Map.entry("property-specification@name", PROPERTY_NAME),
            Map.entry("component@property", PROPERTY_NAME),
            Map.entry("bean@property", PROPERTY_NAME),
            Map.entry("asset@property", PROPERTY_NAME),
            Map.entry("parameter@property", PROPERTY_NAME),
            Map.entry("parameter@property-name", PROPERTY_NAME),
            Map.entry("inject@property", PROPERTY_NAME),
            Map.entry("configure@property", PROPERTY_NAME),
            Map.entry("configure@property-name", PROPERTY_NAME),
            Map.entry("library@id", identifier("Invalid library id '%s': must be a Java identifier")),
            Map.entry("page@name", new NameRule(PAGE_NAME,
                    "Invalid page name '%s': must be a Java identifier, optionally followed by '/'-separated names that may also contain '-'")),
            Map.entry("component-type@type", new NameRule(COMPONENT_ALIAS,
                    "Invalid component type '%s': must be Java identifiers separated by '/'")),
            Map.entry("component@type", new NameRule(COMPONENT_TYPE,
                    "Invalid component type '%s': must be Java identifiers separated by '/', optionally with a library prefix ('contrib:Table')")),
            Map.entry("extension@name", new NameRule(EXTENSION_NAME,
                    "Invalid extension name '%s': must be names separated by '.', each starting with a letter and containing only letters, digits, '_' and '-'")));

    /**
     * Regeln, die Tapestry nur ab der 4.0-DTD anwendet: in der 3.0-DTD ist {@code <property>} das spätere
     * {@code <meta>} mit beliebigem Schlüssel, und {@code SpecificationParser.enterBinding30} prüft keine Namen.
     */
    private static final Set<String> DTD_40_RULES = Set.of("property@name", "binding@name");

    private SpecificationChecks() {
    }

    private static NameRule identifier(String message) {
        return new NameRule(ComponentUsageChecks.IDENTIFIER, message);
    }

    static void checkTag(XmlTag tag, AnnotationHolder holder) {
        if (tag.getParentTag() == null) return;
        switch (tag.getName()) {
            case TAG_COMPONENT -> checkTypeOrCopyOf(tag, holder);
            case TAG_PARAMETER -> checkRequiredWithDefault(tag, holder);
            default -> {
            }
        }
    }

    /** {@code <component>} braucht entweder {@code type} oder {@code copy-of}, nicht beide ({@code SpecificationParser.enterComponent}). */
    private static void checkTypeOrCopyOf(XmlTag tag, AnnotationHolder holder) {
        final boolean hasType = SpecXml.attr(tag, ATTR_TYPE) != null;
        final boolean hasCopyOf = SpecXml.attr(tag, ATTR_COPY_OF) != null;
        if (hasType != hasCopyOf) return;
        holder.newAnnotation(HighlightSeverity.ERROR, hasType ? "Component must not have both a type and a copy-of attribute"
                : "Component needs either a type or a copy-of attribute").range(nameElement(tag)).create();
    }

    /** Ein Pflichtparameter darf keinen Default haben ({@code EstablishDefaultParameterValuesVisitor}). */
    private static void checkRequiredWithDefault(XmlTag tag, AnnotationHolder holder) {
        if (!SpecXml.isTrue(tag.getAttributeValue(ATTR_REQUIRED)) || SpecXml.attr(tag, ATTR_DEFAULT_VALUE) == null) return;
        holder.newAnnotation(HighlightSeverity.ERROR, "Required parameter '%s' must not have a default value"
                .formatted(String.valueOf(SpecXml.attr(tag, ATTR_NAME)))).range(nameElement(tag)).create();
    }

    static void checkAttribute(XmlAttributeValue value, XmlAttribute attribute, AnnotationHolder holder) {
        final XmlTag tag = attribute.getParent();
        if (tag == null) return;
        final String text = value.getValue().trim();
        final String key = SpecXml.attributeKey(tag.getName(), attribute.getName());
        if (!text.isEmpty() && !(DTD_40_RULES.contains(key) && SpecXml.isDtd30(tag.getContainingFile()))) checkName(key, text, holder);
        DeclarationChecks.checkAttribute(value, holder);
        switch (key) {
            case "component@id" -> {
                if (!text.isEmpty()) ComponentUsageChecks.checkComponentId(holder, text);
            }
            case "component@type" -> {
                final XmlFile spec = TapestryRegistry.resolveComponentType(text, value);
                if (spec != null) ComponentUsageChecks.checkDeprecatedComponent(holder, spec, text, value);
            }
            case "component@copy-of" -> {
                if (!text.isEmpty()) checkCopyOfOrder(tag, text, holder);
            }
            case "component@inherit-informal-parameters" -> checkInheritInformal(tag, text, holder);
            case "library@id" -> {
                if (RESERVED_LIBRARY_IDS.contains(text)) {
                    holder.newAnnotation(HighlightSeverity.ERROR, "Library id '%s' is reserved by Tapestry".formatted(text)).create();
                }
            }
            default -> {
            }
        }
        final XmlTag component = tag.getParentTag();
        if (BINDING_TAGS.contains(tag.getName()) && ATTR_NAME.equals(attribute.getName()) && component != null
                && TAG_COMPONENT.equals(component.getName())) {
            final String type = ComponentModel.getComponentTypeOfSpecTag(component);
            final XmlFile spec = type != null ? TapestryRegistry.resolveComponentType(type, value) : null;
            if (spec != null) {
                ComponentUsageChecks.checkParameterUsage(holder, spec, type, text, false, Set.of(), value);
                checkBoundUnderOtherName(tag, component, spec, type, text, holder);
            }
        }
    }

    private static void checkName(String key, String text, AnnotationHolder holder) {
        final NameRule rule = NAME_RULES.get(key);
        if (rule == null || "asset@name".equals(key) && TEMPLATE_ASSET.equals(text) || rule.pattern().matcher(text).matches()) return;
        holder.newAnnotation(HighlightSeverity.ERROR, rule.message().formatted(text)).create();
    }

    /**
     * copy-of übernimmt beim Parsen die Bindings einer Komponente, die bis dahin in derselben Datei deklariert ist
     * ({@code SpecificationParser.copyBindings}); später deklarierte und per {@code @Component} deklarierte
     * Komponenten gibt es zu diesem Zeitpunkt noch nicht. Unbekannte ids meldet die Referenz.
     */
    private static void checkCopyOfOrder(XmlTag tag, String id, AnnotationHolder holder) {
        final XmlTag root = tag.getParentTag();
        if (root == null) return;
        for (final XmlTag sibling : root.findSubTags(TAG_COMPONENT)) {
            if (id.equals(SpecXml.attr(sibling, ATTR_ID))) return;
            if (sibling == tag) break;
        }
        if (ComponentModel.findDeclaredComponent(TapestryModel.getContext(tag.getContainingFile()), id) == null) return;
        holder.newAnnotation(HighlightSeverity.ERROR, "Component '%s' must be declared earlier in this file to be copied".formatted(id)).create();
    }

    /**
     * {@code inherit-informal-parameters} verlangt, dass die Komponente und ihr Container informelle Parameter
     * erlauben ({@code PageLoader.bind}).
     */
    private static void checkInheritInformal(XmlTag component, String flag, AnnotationHolder holder) {
        if (!SpecXml.isTrue(flag)) return;
        final String type = ComponentModel.getComponentTypeOfSpecTag(component);
        final XmlFile spec = type != null ? TapestryRegistry.resolveComponentType(type, component) : null;
        if (spec != null && !ComponentModel.allowsInformalParameters(spec)) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Component '%s' allows only formal parameters and cannot inherit informal parameters"
                    .formatted(type)).create();
        } else if (!ComponentModel.allowsInformalParameters(component.getContainingFile())) {
            holder.newAnnotation(HighlightSeverity.ERROR, "%s allows only formal parameters, so its components cannot inherit informal parameters"
                    .formatted(component.getContainingFile().getName())).create();
        }
    }

    /**
     * Ein Parameter darf nur einmal gebunden werden – auch nicht unter Name und Alias
     * ({@code PageLoader.addBindingToComponent}). Derselbe Name zweimal überschreibt still das erste Binding.
     */
    private static void checkBoundUnderOtherName(XmlTag binding, XmlTag component, XmlFile spec, String type, String name,
                                                 AnnotationHolder holder) {
        final ComponentParameter parameter = ComponentModel.findParameter(spec, name);
        if (parameter == null) return;
        for (final XmlTag sibling : component.getSubTags()) {
            if (sibling == binding) return;
            final String other = BINDING_TAGS.contains(sibling.getName()) ? SpecXml.attr(sibling, ATTR_NAME) : null;
            if (other != null && !other.equalsIgnoreCase(name) && parameter.matches(other)) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Parameter '%s' of component '%s' is already bound as '%s'"
                        .formatted(name, type, other)).create();
                return;
            }
        }
    }

    private static PsiElement nameElement(XmlTag tag) {
        final PsiElement name = XmlTagUtil.getStartTagNameElement(tag);
        return name != null ? name : tag;
    }
}
