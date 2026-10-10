package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.ComponentModel;
import com.herreromarcos.idea.tapestry4plugin.model.JavaClasses;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Glied einer OGNL-Kette, z.B. {@code user}, {@code address}, {@code city} in {@code ognl:user.address.city}.
 * <ul>
 *   <li>das erste Glied wird gegen die Seiten-/Komponentenklasse (bzw. die aus der Spezifikation erzeugten
 *       Eigenschaften) aufgelöst, jedes weitere gegen den Typ des Vorgängers</li>
 *   <li>nach Indizes ({@code items[0].name}) gilt der Elementtyp von Array, List bzw. Map</li>
 *   <li>bei {@code @Klasse@member} ist das erste Glied ein statisches Feld bzw. eine statische Methode</li>
 *   <li>OGNL-Pseudo-Properties wie {@code list.size} oder {@code map.keys} werden auf die Methoden abgebildet</li>
 * </ul>
 */
public class OgnlPropertyReference extends TapestryReferenceBase {
    /** OGNL-Pseudo-Properties ("Pseudo-Properties for Collections"): Typ → Name → Methode. */
    private static final List<PseudoProperty> PSEUDO_PROPERTIES = List.of(
            new PseudoProperty("java.util.Collection", "size", "size"),
            new PseudoProperty("java.util.Collection", "isEmpty", "isEmpty"),
            new PseudoProperty("java.util.Collection", "iterator", "iterator"),
            new PseudoProperty("java.util.Map", "size", "size"),
            new PseudoProperty("java.util.Map", "isEmpty", "isEmpty"),
            new PseudoProperty("java.util.Map", "keys", "keySet"),
            new PseudoProperty("java.util.Map", "values", "values"),
            new PseudoProperty("java.util.Iterator", "next", "next"),
            new PseudoProperty("java.util.Iterator", "hasNext", "hasNext"),
            new PseudoProperty("java.util.Enumeration", "next", "nextElement"),
            new PseudoProperty("java.util.Enumeration", "hasNext", "hasMoreElements"),
            new PseudoProperty("java.util.Enumeration", "nextElement", "nextElement"),
            new PseudoProperty("java.util.Enumeration", "hasMoreElements", "hasMoreElements"));

    private final @Nullable OgnlPropertyReference previous;
    private final @Nullable String staticClass;
    private final boolean methodCall;
    private final int indexCount;

    /**
     * @param previous    Vorgänger in der Kette oder {@code null} für das erste Glied
     * @param staticClass Klasse bei {@code @Klasse@member} (nur für das erste Glied)
     * @param methodCall  {@code name(...)}
     * @param indexCount  Anzahl der Indizes zwischen Vorgänger und diesem Glied
     */
    public OgnlPropertyReference(@NotNull PsiElement element, @NotNull TextRange range,
                                 @Nullable OgnlPropertyReference previous, @Nullable String staticClass,
                                 boolean methodCall, int indexCount) {
        super(element, range);
        this.previous = previous;
        this.staticClass = staticClass;
        this.methodCall = methodCall;
        this.indexCount = indexCount;
    }

    private record PseudoProperty(String owner, String property, String method) {
    }

    private record Target(PsiElement element, @Nullable PsiType type) {
    }

    /** Qualifier: Klasse + Substitutor für Generics; {@code known} = der Typ ließ sich bestimmen. */
    private record Qualifier(@Nullable PsiClass psiClass, PsiSubstitutor substitutor, boolean known, boolean staticOnly) {
        static final Qualifier UNKNOWN = new Qualifier(null, PsiSubstitutor.EMPTY, false, false);
    }

    private Qualifier qualifier() {
        if (staticClass != null) {
            // OGNL löst einfache Klassennamen zusätzlich über java.lang auf
            final PsiClass cls = JavaClasses.find(staticClass, getElement());
            return new Qualifier(cls != null || staticClass.contains(".") ? cls : JavaClasses.find("java.lang.%s".formatted(staticClass), getElement()),
                    PsiSubstitutor.EMPTY, true, true);
        }
        if (previous == null) {
            return indexCount == 0 ? new Qualifier(context().effectiveClass(), PsiSubstitutor.EMPTY, true, false) : Qualifier.UNKNOWN;
        }
        final Target target = previous.resolveTarget();
        PsiType type = target != null ? target.type() : null;
        for (int i = 0; i < indexCount && type != null; i++) {
            type = elementType(type);
        }
        if (!(type instanceof final PsiClassType classType)) return Qualifier.UNKNOWN;
        final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
        return new Qualifier(result.getElement(), result.getSubstitutor(), true, false);
    }

    /** Typ von {@code value[index]}: Komponente eines Arrays, Element einer List, Wert einer Map. */
    private static @Nullable PsiType elementType(PsiType type) {
        if (type instanceof final PsiArrayType array) return array.getComponentType();
        final PsiType listElement = PsiUtil.substituteTypeParameter(type, "java.util.List", 0, false);
        return listElement != null ? listElement : PsiUtil.substituteTypeParameter(type, "java.util.Map", 1, false);
    }

    private @Nullable Target resolveTarget() {
        final String name = getValue().trim();
        final Qualifier q = qualifier();
        final PsiClass cls = q.psiClass();
        if (cls != null) {
            final Target member = q.staticOnly() ? resolveStatic(cls, name) : resolveMember(cls, q.substitutor(), name);
            if (member != null) return member;
        }
        if (previous == null && staticClass == null && indexCount == 0) {
            // Eigenschaften, die Tapestry aus der Spezifikation erzeugt (<property>, <parameter>, property-Attribute)
            final PsiElement property = ComponentModel.getSpecProperties(context().spec()).get(name);
            if (property != null) return new Target(property, null);
        }
        return null;
    }

    private @Nullable Target resolveStatic(PsiClass cls, String name) {
        if (methodCall) {
            for (final PsiMethod method : cls.findMethodsByName(name, true)) {
                if (method.hasModifierProperty(PsiModifier.STATIC)) return new Target(method, method.getReturnType());
            }
            return null;
        }
        final PsiField field = cls.findFieldByName(name, true);
        return field != null && field.hasModifierProperty(PsiModifier.STATIC) ? new Target(field, field.getType()) : null;
    }

    private @Nullable Target resolveMember(PsiClass cls, PsiSubstitutor substitutor, String name) {
        if (methodCall) {
            final PsiMethod[] methods = cls.findMethodsByName(name, true);
            return methods.length > 0 ? new Target(methods[0], substitutor.substitute(methods[0].getReturnType())) : null;
        }
        final PsiMethod getter = PropertyUtilBase.findPropertyGetter(cls, name, false, true);
        if (getter != null) return new Target(getter, substitutor.substitute(getter.getReturnType()));
        // Nur ein Setter reicht: Tapestry erzeugt den fehlenden Getter (User's Guide, "Persistent page properties")
        final PsiMethod setter = PropertyUtilBase.findPropertySetter(cls, name, false, true);
        if (setter != null) return new Target(setter, substitutor.substitute(setter.getParameterList().getParameters()[0].getType()));
        final PsiField field = cls.findFieldByName(name, true);
        if (field != null) return new Target(field, substitutor.substitute(field.getType()));
        for (final PseudoProperty pseudo : PSEUDO_PROPERTIES) {
            if (!pseudo.property().equals(name) || !InheritanceUtil.isInheritor(cls, pseudo.owner())) continue;
            for (final PsiMethod method : cls.findMethodsByName(pseudo.method(), true)) {
                if (method.getParameterList().isEmpty()) return new Target(method, substitutor.substitute(method.getReturnType()));
            }
        }
        return null;
    }

    @Override
    public @Nullable PsiElement resolve() {
        final Target target = resolveTarget();
        return target != null ? target.element() : null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        final Map<String, LookupElement> result = new LinkedHashMap<>();
        final Qualifier q = qualifier();
        if (previous == null && staticClass == null && indexCount == 0) {
            for (final Map.Entry<String, XmlAttributeValue> property : ComponentModel.getSpecProperties(context().spec()).entrySet()) {
                final XmlTag tag = PsiTreeUtil.getParentOfType(property.getValue(), XmlTag.class);
                result.put(property.getKey(), LookupElementBuilder.create(property.getKey()).withIcon(TapestryIcons.TAPESTRY)
                        .withTypeText(tag != null ? "<%s>".formatted(tag.getName()) : "", true).bold());
            }
        }
        final PsiClass cls = q.psiClass();
        if (cls != null) {
            for (final PsiMethod m : cls.getAllMethods()) {
                if (m.hasModifierProperty(PsiModifier.STATIC) != q.staticOnly()) continue;
                if (q.staticOnly()) {
                    result.putIfAbsent(m.getName(), LookupElementBuilder.create(m, m.getName()).withIcon(PlatformIcons.METHOD_ICON));
                    continue;
                }
                if (!PropertyUtilBase.isSimplePropertyAccessor(m)) continue;
                final String property = PropertyUtilBase.getPropertyName(m);
                if (property == null || "class".equals(property)) continue;
                final PsiType type = q.substitutor().substitute(PropertyUtilBase.getPropertyType(m));
                result.putIfAbsent(property, LookupElementBuilder.create(m, property).withIcon(PlatformIcons.PROPERTY_ICON)
                        .withTypeText(type != null ? type.getPresentableText() : "", true));
            }
            for (final PsiField f : cls.getAllFields()) {
                if (f.hasModifierProperty(PsiModifier.PUBLIC) && f.hasModifierProperty(PsiModifier.STATIC) == q.staticOnly()) {
                    result.putIfAbsent(f.getName(), LookupElementBuilder.create(f, f.getName()).withIcon(PlatformIcons.FIELD_ICON)
                            .withTypeText(f.getType().getPresentableText(), true));
                }
            }
        }
        return result.values().toArray();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        final PsiElement target = resolve();
        if (!methodCall && target instanceof final PsiMethod method && PropertyUtilBase.isSimplePropertyAccessor(method)) {
            final String property = PropertyUtilBase.getPropertyName(newElementName);
            if (property != null) return super.handleElementRename(property);
        }
        return super.handleElementRename(newElementName);
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        final Qualifier q = qualifier();
        if (q.psiClass() == null || !q.known()) return null;
        if (previous == null && staticClass == null && !context().isKnown()) return null;
        if (!JavaClasses.isHierarchyResolved(q.psiClass())) return null;
        // OGNL greift bei Maps auf Schlüssel zu
        if (!q.staticOnly() && InheritanceUtil.isInheritor(q.psiClass(), "java.util.Map")) return null;
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final Qualifier q = qualifier();
        final String member = methodCall ? "method" : q.staticOnly() ? "field" : "property";
        final String kind = StringUtil.capitalize(q.staticOnly() ? "static " + member : member);
        return "%s '%s' not found in %s".formatted(kind, getValue().trim(), q.psiClass() != null ? q.psiClass().getName() : "?");
    }
}
