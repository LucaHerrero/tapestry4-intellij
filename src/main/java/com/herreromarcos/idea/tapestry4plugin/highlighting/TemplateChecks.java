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
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    /**
     * Wert des jwcid-Attributs: nicht leer, gültige und im Template eindeutige id; die id einer impliziten
     * Komponente darf nicht schon in Spezifikation oder Klasse deklariert sein
     * ({@code ComponentTemplateLoaderLogic.checkForDuplicateId}).
     */
    static void checkJwcid(XmlAttributeValue value, AnnotationHolder holder) {
        if (value.getValue().isBlank()) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Empty jwcid").create();
            return;
        }
        final Jwcid jwcid = Jwcid.parse(value.getValue());
        final String id = jwcid.id();
        if (id == null) return;
        ComponentUsageChecks.checkComponentId(holder, id);
        if (ComponentModel.countComponentId((XmlFile) value.getContainingFile(), id) > 1) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Duplicate component id '%s' in template".formatted(id)).create();
        }
        if (!jwcid.isImplicit()) return;
        final TapestryContext ctx = TapestryModel.getContext(value.getContainingFile());
        if (ComponentModel.findDeclaredComponent(ctx, id) != null) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Component '%s' is already declared in %s".formatted(id, ctx.displayName()))
                    .range(jwcid.idRange().shiftRight(value.getTextRange().getStartOffset()
                            + ElementManipulators.getValueTextRange(value).getStartOffset())).create();
        }
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
            holder.newAnnotation(HighlightSeverity.ERROR, "Required parameter '%s' of component '%s' is not bound"
                            .formatted(parameter.name(), type))
                    .range(jwcidValue).create();
        }
    }

    /**
     * Veraltete Komponente sowie die Regeln aus {@link ComponentUsageChecks#checkParameterUsage} für jedes Attribut.
     * Bindet ein weiteres Attribut denselben Parameter (Name und Alias), ist ein Ausdruck ein Fehler: Tapestry
     * ignoriert nur Literale, die auf ein bestehendes Binding treffen ({@code ComponentTemplateLoaderLogic.validate}),
     * und die Reihenfolge der Attribute ist dort nicht festgelegt.
     */
    private static void checkTemplateParameters(XmlTag tag, XmlFile spec, String type, Set<String> specBound,
                                                XmlAttributeValue jwcidValue, AnnotationHolder holder) {
        ComponentUsageChecks.checkDeprecatedComponent(holder, spec, type, jwcidValue);
        final Map<String, List<String>> attributesByParameter = new HashMap<>();
        for (final XmlAttribute attribute : tag.getAttributes()) {
            final ComponentParameter parameter = ComponentModel.findParameter(spec, attribute.getName());
            if (parameter != null) attributesByParameter.computeIfAbsent(parameter.name(), k -> new ArrayList<>()).add(attribute.getName());
        }
        for (final XmlAttribute attribute : tag.getAttributes()) {
            if (attribute.getValueElement() == jwcidValue || attribute.getNameElement() == null) continue;
            final boolean literal = BindingExpression.parse(StringUtil.notNullize(attribute.getValue()), tag).isLiteral();
            ComponentUsageChecks.checkParameterUsage(holder, spec, type, attribute.getName(), literal, specBound, attribute.getNameElement());
            final ComponentParameter parameter = ComponentModel.findParameter(spec, attribute.getName());
            final List<String> attributes = parameter != null ? attributesByParameter.get(parameter.name()) : List.of();
            if (!literal && attributes.size() > 1) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Parameter '%s' of component '%s' is bound more than once (%s)"
                                .formatted(parameter.name(), type, String.join(", ", attributes)))
                        .range(attribute.getNameElement()).create();
            }
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
