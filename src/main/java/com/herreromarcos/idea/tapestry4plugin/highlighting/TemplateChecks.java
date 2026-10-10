package com.herreromarcos.idea.tapestry4plugin.highlighting;

import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentParameter;
import com.herreromarcos.idea.tapestry4plugin.model.DeclaredComponent;
import com.herreromarcos.idea.tapestry4plugin.model.Jwcid;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.REMOVE_ID;

/** Prüfungen in Templates: jwcid-Syntax, eindeutige ids, Platzierung, Pflichtparameter und Template-Attribute. */
class TemplateChecks {

    private TemplateChecks() {
    }

    /** Prüfungen eines Komponenten-Tags; andere Tags werden ignoriert. */
    static void checkTag(XmlTag tag, AnnotationHolder holder) {
        final XmlAttributeValue jwcidValue = TapestryConfiguration.findJwcidValueElement(tag);
        if (jwcidValue == null) return;
        final Jwcid jwcid = Jwcid.parse(jwcidValue.getValue());
        if (jwcid.special()) return;
        checkComponentPlacement(tag, jwcidValue, holder);
        final XmlFile spec = ComponentModel.getComponentSpecOfTag(tag);
        if (spec == null) return;
        final String type = ComponentModel.getComponentTypeOfTag(tag);
        final Set<String> specBound = specBoundParameters(tag, jwcid);
        checkRequiredParameters(tag, spec, type, specBound, jwcidValue, holder);
        checkTemplateParameters(tag, spec, type, specBound, jwcidValue, holder);
    }

    /** Wert des jwcid-Attributs: nicht leer, gültige und im Template eindeutige id. */
    static void checkJwcid(XmlAttributeValue value, AnnotationHolder holder) {
        if (value.getValue().isBlank()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Empty jwcid").create();
            return;
        }
        final String id = Jwcid.parse(value.getValue()).id();
        if (id == null) return;
        ComponentUsageChecks.checkComponentId(holder, id);
        if (idCounts((XmlFile) value.getContainingFile()).getOrDefault(id, 0) > 1) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate component id '%s' in template".formatted(id)).create();
        }
    }

    /** Wie oft kommt jede Komponenten-id ("foo", "foo@Insert") im Template vor? */
    private static Map<String, Integer> idCounts(XmlFile file) {
        return CachedValuesManager.getCachedValue(file, () -> {
            final Map<String, Integer> counts = new HashMap<>();
            for (final XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
                final String value = TapestryConfiguration.findJwcidValue(tag);
                final String id = value != null ? Jwcid.parse(value).id() : null;
                if (id != null) counts.merge(id, 1, Integer::sum);
            }
            return CachedValueProvider.Result.create(counts, file);
        });
    }

    /**
     * Laufzeitfehler laut User's Guide ("Templates"): Komponenten innerhalb eines {@code $remove$}-Blocks und im
     * Body einer Komponente, die ihren Body verwirft ({@code allow-body="no"}, z.B. Insert).
     */
    private static void checkComponentPlacement(XmlTag tag, XmlAttributeValue jwcidValue, AnnotationHolder holder) {
        boolean nearestComponentChecked = false;
        for (XmlTag ancestor = tag.getParentTag(); ancestor != null; ancestor = ancestor.getParentTag()) {
            final String value = TapestryConfiguration.findJwcidValue(ancestor);
            if (value == null) continue;
            if (REMOVE_ID.equals(value.trim())) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Components are not allowed inside a %s block".formatted(REMOVE_ID))
                        .range(jwcidValue).create();
                return;
            }
            if (Jwcid.parse(value).special() || nearestComponentChecked) continue;
            nearestComponentChecked = true;
            final XmlFile containerSpec = ComponentModel.getComponentSpecOfTag(ancestor);
            if (containerSpec != null && !ComponentModel.allowsBody(containerSpec)) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Component '%s' discards its body; components are not allowed inside it"
                                .formatted(ComponentModel.getComponentTypeOfTag(ancestor)))
                        .range(jwcidValue).create();
            }
        }
    }

    /** Pflichtparameter müssen im Template oder – bei deklarierten Komponenten – in der Spezifikation gebunden sein. */
    private static void checkRequiredParameters(XmlTag tag, XmlFile spec, String type, Set<String> specBound,
                                                XmlAttributeValue jwcidValue, AnnotationHolder holder) {
        final Set<String> bound = new HashSet<>(specBound);
        for (final XmlAttribute attribute : tag.getAttributes()) {
            bound.add(ComponentModel.normalize(attribute.getName()));
        }
        for (final ComponentParameter parameter : ComponentModel.getParameters(spec)) {
            if (!parameter.required() || ComponentUsageChecks.isBound(parameter, bound)) continue;
            holder.newAnnotation(HighlightSeverity.WARNING, "Required parameter '%s' of component '%s' is not bound"
                            .formatted(parameter.name(), type))
                    .range(jwcidValue).create();
        }
    }

    /** Veraltete Komponente sowie die Regeln aus {@link ComponentUsageChecks#checkParameterUsage} für jedes Attribut. */
    private static void checkTemplateParameters(XmlTag tag, XmlFile spec, String type, Set<String> specBound,
                                                XmlAttributeValue jwcidValue, AnnotationHolder holder) {
        ComponentUsageChecks.checkDeprecatedComponent(holder, spec, type, jwcidValue);
        for (final XmlAttribute attribute : tag.getAttributes()) {
            if (attribute.getValueElement() == jwcidValue || attribute.getNameElement() == null) continue;
            final boolean literal = BindingExpression.parse(StringUtil.notNullize(attribute.getValue()), tag).isLiteral();
            ComponentUsageChecks.checkParameterUsage(holder, spec, type, attribute.getName(), literal, specBound, attribute.getNameElement());
        }
    }

    /** Bei {@code jwcid="foo"} die in Spezifikation bzw. {@code @Component} gebundenen Parameter von foo. */
    private static Set<String> specBoundParameters(XmlTag tag, Jwcid jwcid) {
        if (!jwcid.isDeclaredReference() || jwcid.id() == null) return Set.of();
        final TapestryContext ctx = TapestryModel.getContext(tag.getContainingFile());
        final DeclaredComponent declared = ComponentModel.findDeclaredComponent(ctx, jwcid.id());
        return declared != null ? ComponentModel.getBoundParameters(ctx, declared) : Set.of();
    }
}
