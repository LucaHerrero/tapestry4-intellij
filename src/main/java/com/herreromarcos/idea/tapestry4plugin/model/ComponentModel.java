package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/** Deklarierte Komponenten einer Seite/Komponente und formale Parameter von Komponenten. */
public class ComponentModel {
    /** Schutz gegen zyklische copy-of-Ketten. */
    private static final int MAX_COPY_OF_DEPTH = 10;

    private ComponentModel() {
    }

    // ------------------------------------------------------------------ Deklarierte Komponenten

    /** Komponenten aus {@code <component>} der Spezifikation und {@code @Component} der Klasse (Spezifikation gewinnt). */
    public static @NotNull List<DeclaredComponent> getDeclaredComponents(@NotNull final TapestryContext ctx) {
        final List<DeclaredComponent> result = new ArrayList<>();
        final Set<String> seen = new HashSet<>();
        final XmlTag root = SpecXml.rootTag(ctx.spec());
        if (root != null) {
            for (final XmlTag tag : root.findSubTags(TAG_COMPONENT)) {
                final DeclaredComponent component = fromSpecTag(tag);
                if (component != null && seen.add(component.id())) result.add(component);
            }
        }
        if (ctx.declaredClass() != null) {
            for (final PsiMethod method : ctx.declaredClass().getAllMethods()) {
                final DeclaredComponent component = fromAnnotation(method);
                if (component != null && seen.add(component.id())) result.add(component);
            }
        }
        return result;
    }

    private static @Nullable DeclaredComponent fromSpecTag(final XmlTag tag) {
        final XmlAttribute idAttr = tag.getAttribute(ATTR_ID);
        if (idAttr == null || idAttr.getValueElement() == null) return null;
        final Set<String> bound = new HashSet<>();
        for (final String bindingTag : BINDING_TAGS) {
            for (final XmlTag binding : tag.findSubTags(bindingTag)) {
                final String name = SpecXml.attr(binding, ATTR_NAME);
                if (name != null) bound.add(normalize(name));
            }
        }
        return new DeclaredComponent(StringUtil.notNullize(idAttr.getValue()).trim(), SpecXml.attr(tag, ATTR_TYPE),
                SpecXml.attr(tag, ATTR_COPY_OF), idAttr.getValueElement(), bound);
    }

    private static @Nullable DeclaredComponent fromAnnotation(final PsiMethod method) {
        final PsiAnnotation annotation = method.getModifierList().findAnnotation(ANNOTATION_COMPONENT);
        if (annotation == null) return null;
        final String explicitId = annotationString(annotation, "id");
        final String id = explicitId != null ? explicitId : PropertyUtilBase.getPropertyName(method);
        if (id == null) return null;
        // bindings = {"value=ognl:foo", ...}
        final Set<String> bound = new HashSet<>();
        for (final PsiAnnotationMemberValue value : AnnotationUtil.arrayAttributeValues(annotation.findAttributeValue("bindings"))) {
            if (value instanceof final PsiLiteralExpression literal && literal.getValue() instanceof final String binding && binding.contains("=")) {
                bound.add(normalize(StringUtils.substringBefore(binding, "=")));
            }
        }
        return new DeclaredComponent(id, annotationString(annotation, "type"), annotationString(annotation, "copyOf"), method, bound);
    }

    public static @Nullable DeclaredComponent findDeclaredComponent(@NotNull TapestryContext ctx, @NotNull String id) {
        return getDeclaredComponents(ctx).stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }

    /** Typ einer deklarierten Komponente; copy-of wird verfolgt. */
    public static @Nullable String getEffectiveType(@NotNull final TapestryContext ctx, @NotNull final DeclaredComponent component) {
        DeclaredComponent current = component;
        for (int i = 0; i < MAX_COPY_OF_DEPTH && current != null; i++) {
            if (current.type() != null) return current.type();
            if (current.copyOf() == null) return null;
            current = findDeclaredComponent(ctx, current.copyOf());
        }
        return null;
    }

    /** In Spezifikation/Annotation gebundene Parameter; bei copy-of inklusive der Bindings des Originals. */
    public static @NotNull Set<String> getBoundParameters(@NotNull final TapestryContext ctx, @NotNull final DeclaredComponent component) {
        final Set<String> bound = new HashSet<>();
        DeclaredComponent current = component;
        for (int i = 0; i < MAX_COPY_OF_DEPTH && current != null; i++) {
            bound.addAll(current.boundParameters());
            current = current.copyOf() != null ? findDeclaredComponent(ctx, current.copyOf()) : null;
        }
        return bound;
    }

    /** Komponententyp eines Template-Tags mit jwcid ({@code @Insert}, {@code id@Insert} oder deklarierte id). */
    public static @Nullable String getComponentTypeOfTag(@NotNull final XmlTag tag) {
        final String value = TapestryConfiguration.findJwcidValue(tag);
        if (value == null) return null;
        final Jwcid jwcid = Jwcid.parse(value);
        if (jwcid.isImplicit()) return jwcid.type();
        if (!jwcid.isDeclaredReference() || jwcid.id() == null) return null;
        final TapestryContext ctx = TapestryModel.getContext(tag.getContainingFile());
        final DeclaredComponent declared = findDeclaredComponent(ctx, jwcid.id());
        return declared != null ? getEffectiveType(ctx, declared) : null;
    }

    /** Spezifikation (.jwc) der Komponente eines Template-Tags mit jwcid. */
    public static @Nullable XmlFile getComponentSpecOfTag(@NotNull final XmlTag tag) {
        if (!TapestryConfiguration.isComponentTag(tag)) return null;
        final String type = getComponentTypeOfTag(tag);
        return type != null ? TapestryRegistry.resolveComponentType(type, tag) : null;
    }

    // ------------------------------------------------------------------ Parameter

    /** Formale Parameter: {@code <parameter>} der .jwc plus {@code @Parameter} der Komponentenklasse. */
    public static @NotNull List<ComponentParameter> getParameters(@NotNull final PsiFile componentSpec) {
        if (!(componentSpec instanceof final XmlFile xml)) return List.of();
        return CachedValuesManager.getCachedValue(xml, () ->
                CachedValueProvider.Result.create(computeParameters(xml), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static List<ComponentParameter> computeParameters(final XmlFile spec) {
        final List<ComponentParameter> result = new ArrayList<>();
        final Set<String> names = new HashSet<>();
        final XmlTag root = spec.getRootTag();
        if (root != null) {
            for (final XmlTag tag : root.findSubTags(TAG_PARAMETER)) {
                final XmlAttribute nameAttr = tag.getAttribute(ATTR_NAME);
                final String name = SpecXml.attr(tag, ATTR_NAME);
                if (name == null || nameAttr.getValueElement() == null || !names.add(name)) continue;
                result.add(new ComponentParameter(name, SpecXml.isTrue(tag.getAttributeValue("required")),
                        SpecXml.splitList(tag.getAttributeValue("aliases")), SpecXml.isTrue(tag.getAttributeValue("deprecated")),
                        nameAttr.getValueElement()));
            }
        }
        final PsiClass cls = TapestryModel.getContext(spec).declaredClass();
        if (cls != null) {
            for (final PsiMethod method : cls.getAllMethods()) {
                final PsiAnnotation annotation = method.getModifierList().findAnnotation(ANNOTATION_PARAMETER);
                if (annotation == null) continue;
                final String explicitName = annotationString(annotation, "name");
                final String name = explicitName != null ? explicitName : PropertyUtilBase.getPropertyName(method);
                if (name == null || !names.add(name)) continue;
                result.add(new ComponentParameter(name, Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "required")),
                        SpecXml.splitList(AnnotationUtil.getStringAttributeValue(annotation, "aliases")),
                        method.isDeprecated(), method));
            }
        }
        return result;
    }

    public static @Nullable ComponentParameter findParameter(@NotNull PsiFile componentSpec, @NotNull String name) {
        return getParameters(componentSpec).stream().filter(p -> p.matches(name)).findFirst().orElse(null);
    }

    public static boolean allowsInformalParameters(@NotNull final PsiFile componentSpec) {
        return componentFlag(componentSpec, "allow-informal-parameters", "allowInformalParameters");
    }

    /** {@code allow-body="no"} ("Body: removed"): der Body wird verworfen, Komponenten darin sind ein Fehler. */
    public static boolean allowsBody(@NotNull final PsiFile componentSpec) {
        return componentFlag(componentSpec, "allow-body", "allowBody");
    }

    /** {@code deprecated="yes"} der Spezifikation bzw. {@code @ComponentClass} mit {@code @Deprecated}: Verwendung erzeugt eine Warnung. */
    public static boolean isDeprecated(@NotNull final PsiFile componentSpec) {
        final PsiClass cls = componentClassWithAnnotation(componentSpec);
        if (cls != null && cls.isDeprecated()) return true;
        final XmlTag root = SpecXml.rootTag(componentSpec);
        return root != null && SpecXml.isTrue(root.getAttributeValue("deprecated"));
    }

    /**
     * Namen, die nicht als informelle Parameter erlaubt sind (Vergleich ohne Groß-/Kleinschreibung):
     * {@code <reserved-parameter>}, {@code @ComponentClass(reservedParameters = "...")} – formale Parameter sind
     * laut Doku automatisch reserviert und werden separat behandelt.
     */
    public static @NotNull Set<String> getReservedParameters(@NotNull final PsiFile componentSpec) {
        final Set<String> result = new HashSet<>();
        final XmlTag root = SpecXml.rootTag(componentSpec);
        if (root != null) {
            for (final XmlTag tag : root.findSubTags("reserved-parameter")) {
                final String name = SpecXml.attr(tag, ATTR_NAME);
                if (name != null) result.add(normalize(name));
            }
        }
        final PsiClass cls = componentClassWithAnnotation(componentSpec);
        final PsiAnnotation componentClass = cls != null ? cls.getModifierList().findAnnotation(ANNOTATION_COMPONENT_CLASS) : null;
        if (componentClass != null) {
            SpecXml.splitList(AnnotationUtil.getStringAttributeValue(componentClass, "reservedParameters"))
                    .forEach(name -> result.add(normalize(name)));
        }
        return result;
    }

    private static @Nullable PsiClass componentClassWithAnnotation(final PsiFile componentSpec) {
        final PsiClass cls = TapestryModel.getContext(componentSpec).declaredClass();
        return cls != null && cls.getModifierList() != null && cls.getModifierList().findAnnotation(ANNOTATION_COMPONENT_CLASS) != null
                ? cls : null;
    }

    /** Flag aus {@code @ComponentClass} (überschreibt die Spezifikation, Default true) bzw. aus dem Wurzel-Tag. */
    private static boolean componentFlag(final PsiFile componentSpec, final String specAttribute, final String annotationAttribute) {
        final PsiClass cls = TapestryModel.getContext(componentSpec).declaredClass();
        final PsiAnnotation componentClass = cls != null ? cls.getModifierList() != null
                ? cls.getModifierList().findAnnotation(ANNOTATION_COMPONENT_CLASS) : null : null;
        if (componentClass != null) {
            return !Boolean.FALSE.equals(AnnotationUtil.getBooleanAttributeValue(componentClass, annotationAttribute));
        }
        final XmlTag root = SpecXml.rootTag(componentSpec);
        return root == null || !SpecXml.isFalse(root.getAttributeValue(specAttribute));
    }

    // ------------------------------------------------------------------ Von Tapestry erzeugte Eigenschaften

    /**
     * Eigenschaften, die Tapestry zur Laufzeit aus der Spezifikation erzeugt (ohne Java-Deklaration):
     * {@code <property name>}, jeder {@code <parameter>} (Name oder {@code property}-Attribut) sowie das
     * {@code property}-Attribut von {@code <inject>}, {@code <bean>}, {@code <asset>} und {@code <component>}.
     * Aus der 3.0-DTD zusätzlich {@code <property-specification>} und {@code <parameter property-name>}.
     */
    public static @NotNull Map<String, XmlAttributeValue> getSpecProperties(@Nullable final XmlFile spec) {
        final XmlTag root = SpecXml.rootTag(spec);
        if (root == null) return Map.of();
        final Map<String, XmlAttributeValue> result = new LinkedHashMap<>();
        for (final XmlTag tag : root.getSubTags()) {
            final XmlAttribute attribute = switch (tag.getName()) {
                case TAG_PROPERTY, TAG_PROPERTY_SPECIFICATION -> tag.getAttribute(ATTR_NAME);
                case TAG_PARAMETER -> firstPresent(tag, ATTR_PROPERTY, ATTR_PROPERTY_NAME, ATTR_NAME);
                case TAG_INJECT, TAG_BEAN, TAG_ASSET, TAG_COMPONENT -> tag.getAttribute(ATTR_PROPERTY);
                default -> null;
            };
            final XmlAttributeValue value = attribute != null ? attribute.getValueElement() : null;
            if (value != null && !value.getValue().isBlank()) result.putIfAbsent(value.getValue().trim(), value);
        }
        return result;
    }

    private static @Nullable XmlAttribute firstPresent(final XmlTag tag, final String... names) {
        for (final String name : names) {
            final XmlAttribute attribute = tag.getAttribute(name);
            if (attribute != null && !StringUtil.isEmptyOrSpaces(attribute.getValue())) return attribute;
        }
        return null;
    }

    /** Getter mit der Annotation (z.B. {@code @Asset}, {@code @Bean}); der Name ist der Eigenschaftsname. */
    public static @NotNull Map<String, PsiMethod> getAnnotatedProperties(@Nullable final PsiClass cls, @NotNull final String annotation) {
        if (cls == null) return Map.of();
        final Map<String, PsiMethod> result = new LinkedHashMap<>();
        for (final PsiMethod method : cls.getAllMethods()) {
            if (method.getModifierList().findAnnotation(annotation) == null) continue;
            final String property = PropertyUtilBase.getPropertyName(method);
            if (property != null) result.putIfAbsent(property, method);
        }
        return result;
    }

    /** Parameternamen werden case-insensitiv verglichen (HTML-Attribute). */
    public static @NotNull String normalize(@NotNull final String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }

    private static @Nullable String annotationString(final PsiAnnotation annotation, final String attribute) {
        return StringUtil.nullize(AnnotationUtil.getStringAttributeValue(annotation, attribute));
    }
}
