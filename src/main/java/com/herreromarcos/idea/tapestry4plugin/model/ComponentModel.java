package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
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
        for (final String bindingTag : List.of(TAG_BINDING, TAG_INHERITED_BINDING)) {
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
        String id = annotationString(annotation, "id");
        if (id == null) id = PropertyUtilBase.getPropertyName(method);
        if (id == null) return null;
        // bindings = {"value=ognl:foo", ...}
        final Set<String> bound = new HashSet<>();
        for (final PsiAnnotationMemberValue value : AnnotationUtil.arrayAttributeValues(annotation.findAttributeValue("bindings"))) {
            if (value instanceof final PsiLiteralExpression literal && literal.getValue() instanceof final String binding && binding.contains("=")) {
                bound.add(normalize(binding.substring(0, binding.indexOf('='))));
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
        final String value = tag.getAttributeValue(JWCID);
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
        if (tag.getAttribute(JWCID) == null) return null;
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
                        SpecXml.splitList(tag.getAttributeValue("aliases")), nameAttr.getValueElement()));
            }
        }
        final PsiClass cls = TapestryModel.getContext(spec).declaredClass();
        if (cls != null) {
            for (final PsiMethod method : cls.getAllMethods()) {
                final PsiAnnotation annotation = method.getModifierList().findAnnotation(ANNOTATION_PARAMETER);
                if (annotation == null) continue;
                String name = annotationString(annotation, "name");
                if (name == null) name = PropertyUtilBase.getPropertyName(method);
                if (name == null || !names.add(name)) continue;
                result.add(new ComponentParameter(name, Boolean.TRUE.equals(AnnotationUtil.getBooleanAttributeValue(annotation, "required")),
                        SpecXml.splitList(AnnotationUtil.getStringAttributeValue(annotation, "aliases")), method));
            }
        }
        return result;
    }

    public static @Nullable ComponentParameter findParameter(@NotNull PsiFile componentSpec, @NotNull String name) {
        return getParameters(componentSpec).stream().filter(p -> p.matches(name)).findFirst().orElse(null);
    }

    public static boolean allowsInformalParameters(@NotNull final PsiFile componentSpec) {
        final XmlTag root = SpecXml.rootTag(componentSpec);
        return root == null || !SpecXml.isFalse(root.getAttributeValue("allow-informal-parameters"));
    }

    /** Parameternamen werden case-insensitiv verglichen (HTML-Attribute). */
    public static @NotNull String normalize(@NotNull final String parameterName) {
        return parameterName.trim().toLowerCase(Locale.ROOT);
    }

    private static @Nullable String annotationString(final PsiAnnotation annotation, final String attribute) {
        return StringUtil.nullize(AnnotationUtil.getStringAttributeValue(annotation, attribute));
    }
}
