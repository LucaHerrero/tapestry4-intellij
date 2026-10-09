package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PlatformIcons;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.JavaClasses;
import com.herreromarcos.idea.tapestry4plugin.model.SpecXml;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.ATTR_NAME;
import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.TAG_PROPERTY;

/**
 * Ein Segment einer einfachen OGNL-Kette, z.B. {@code user}, {@code address}, {@code city} in
 * {@code ognl:user.address.city}. Das erste Segment wird gegen die Seiten-/Komponentenklasse
 * (bzw. {@code <property>} der Spezifikation) aufgelöst, jedes weitere gegen den Typ des Vorgängers.
 */
public class OgnlPropertyReference extends TapestryReferenceBase {
    private final @Nullable OgnlPropertyReference previous;
    private final boolean methodCall;

    public OgnlPropertyReference(@NotNull final PsiElement element, @NotNull final TextRange range,
                                 @Nullable final OgnlPropertyReference previous, final boolean methodCall) {
        super(element, range);
        this.previous = previous;
        this.methodCall = methodCall;
    }

    private record Target(PsiElement element, @Nullable PsiType type) {
    }

    /** Qualifier-Typ: Klasse + Substitutor für Generics. */
    private record Qualifier(@Nullable PsiClass psiClass, PsiSubstitutor substitutor, boolean fromPrevious) {
    }

    private Qualifier qualifier() {
        if (previous == null) {
            return new Qualifier(context().effectiveClass(), PsiSubstitutor.EMPTY, false);
        }
        final Target target = previous.resolveTarget();
        if (target == null || !(target.type() instanceof final PsiClassType classType)) {
            return new Qualifier(null, PsiSubstitutor.EMPTY, true);
        }
        final PsiClassType.ClassResolveResult result = classType.resolveGenerics();
        return new Qualifier(result.getElement(), result.getSubstitutor(), true);
    }

    private @Nullable Target resolveTarget() {
        final String name = getValue().trim();
        final Qualifier q = qualifier();
        final PsiClass cls = q.psiClass();
        if (cls != null) {
            if (methodCall) {
                for (final PsiMethod m : cls.findMethodsByName(name, true)) {
                    return new Target(m, q.substitutor().substitute(m.getReturnType()));
                }
            } else {
                final PsiMethod getter = PropertyUtilBase.findPropertyGetter(cls, name, false, true);
                if (getter != null) return new Target(getter, q.substitutor().substitute(getter.getReturnType()));
                final PsiField field = cls.findFieldByName(name, true);
                if (field != null) return new Target(field, q.substitutor().substitute(field.getType()));
            }
        }
        if (previous == null) {
            // <property name="..."/> in der Spezifikation erzeugt eine Eigenschaft ohne Java-Deklaration
            final PsiElement property = SpecXml.findChild(context().spec(), TAG_PROPERTY, ATTR_NAME, name);
            if (property != null) return new Target(property, null);
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
        if (previous == null) {
            for (final String name : SpecXml.childValues(context().spec(), TAG_PROPERTY, ATTR_NAME)) {
                result.put(name, LookupElementBuilder.create(name).withIcon(TapestryIcons.TAPESTRY).withTypeText("<property>", true).bold());
            }
        }
        final PsiClass cls = q.psiClass();
        if (cls != null) {
            for (final PsiMethod m : cls.getAllMethods()) {
                if (m.hasModifierProperty(PsiModifier.STATIC) || !PropertyUtilBase.isSimplePropertyGetter(m)) continue;
                final String property = PropertyUtilBase.getPropertyName(m);
                if (property == null || "class".equals(property)) continue;
                final PsiType type = q.substitutor().substitute(m.getReturnType());
                result.putIfAbsent(property, LookupElementBuilder.create(m, property).withIcon(PlatformIcons.PROPERTY_ICON)
                        .withTypeText(type != null ? type.getPresentableText() : "", true));
            }
            for (final PsiField f : cls.getAllFields()) {
                if (f.hasModifierProperty(PsiModifier.PUBLIC) && !f.hasModifierProperty(PsiModifier.STATIC)) {
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
        if (!methodCall && target instanceof final PsiMethod method && PropertyUtilBase.isSimplePropertyGetter(method)) {
            final String property = PropertyUtilBase.getPropertyName(newElementName);
            if (property != null) newElementName = property;
        }
        return super.handleElementRename(newElementName);
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        final Qualifier q = qualifier();
        if (q.psiClass() == null) return null;
        if (!q.fromPrevious() && !context().isKnown()) return null;
        if (!JavaClasses.isHierarchyResolved(q.psiClass())) return null;
        // OGNL greift bei Maps auf Schlüssel zu
        if (InheritanceUtil.isInheritor(q.psiClass(), "java.util.Map")) return null;
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final PsiClass cls = qualifier().psiClass();
        return "%s '%s' not found in %s".formatted(methodCall ? "Method" : "Property", getValue().trim(), cls != null ? cls.getName() : "?");
    }
}
