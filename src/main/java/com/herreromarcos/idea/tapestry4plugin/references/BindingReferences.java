package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import com.herreromarcos.idea.tapestry4plugin.model.OgnlExpression;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.lang.model.SourceVersion;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;
import static com.herreromarcos.idea.tapestry4plugin.references.TapestryReferenceBase.single;

/** Erzeugt die Referenzen für Binding-Ausdrücke ({@code prefix:expression}). */
class BindingReferences {
    private BindingReferences() {
    }

    /**
     * @param text          Attributwert (ohne Anführungszeichen)
     * @param offset        Offset von {@code text} innerhalb von {@code element}
     * @param defaultPrefix Präfix, wenn keines angegeben ist (Spezifikation: "ognl" bzw. default-binding-prefix, Template: {@code null} = Literal)
     */
    static PsiReference @NotNull [] create(@NotNull PsiElement element, @NotNull String text, int offset,
                                           @Nullable String defaultPrefix) {
        final BindingExpression binding = BindingExpression.parse(text, element);
        final List<PsiReference> result = new ArrayList<>();
        final TextRange prefixRange = prefixRange(binding, offset);
        if (binding.registeredPrefix() != null && !PREFIX_HIVEMIND.equals(binding.prefix())) {
            result.add(new BindingPrefixReference(element, prefixRange));
        } else if (binding.unregisteredPrefix() != null && PREFIX_OGNL.equals(defaultPrefix)) {
            // "foo:bar" ohne registriertes Präfix wäre ein ungültiger OGNL-Ausdruck – nur das Präfix melden
            return single(new BindingPrefixReference(element, prefixRange));
        }
        final String prefix = binding.effectivePrefix(defaultPrefix);
        if (prefix != null) {
            result.addAll(List.of(createForPrefix(element, prefix, binding.expression(), offset + binding.expressionStart())));
        }
        return result.toArray(PsiReference.EMPTY_ARRAY);
    }

    /** Bereich des (registrierten oder unregistrierten) Präfixes ohne Doppelpunkt. */
    private static TextRange prefixRange(BindingExpression binding, int offset) {
        final String text = binding.text();
        final String name = binding.registeredPrefix() != null ? binding.registeredPrefix() : StringUtil.notNullize(binding.unregisteredPrefix());
        return TextRange.from(offset + text.length() - text.stripLeading().length(), name.length());
    }

    private static PsiReference[] createForPrefix(PsiElement element, String prefix, String expression,
                                                  int expressionOffset) {
        if (PREFIX_OGNL.equals(prefix)) return createOgnl(element, expression, expressionOffset);
        if (PREFIX_VALIDATORS.equals(prefix)) return createValidators(element, expression, expressionOffset);

        final String trimmed = expression.trim();
        final int leading = expression.length() - expression.stripLeading().length();
        final TextRange range = TextRange.from(expressionOffset + leading, trimmed.length());
        final PsiReference reference = switch (prefix) {
            case PREFIX_LISTENER -> isSimpleName(trimmed) ? new ListenerReference(element, range) : null;
            case PREFIX_MESSAGE -> new MessageKeyReference(element, range);
            case PREFIX_ASSET -> new SpecChildReference(element, range, SpecChildReference.Kind.ASSET);
            case PREFIX_BEAN -> new SpecChildReference(element, range, SpecChildReference.Kind.BEAN);
            case PREFIX_COMPONENT -> new ComponentIdReference(element, range, false);
            case PREFIX_STATE -> new StateObjectReference(element, range);
            default -> null;
        };
        return single(reference);
    }

    /**
     * OGNL-Ausdruck: pro Glied jeder auflösbaren Kette eine Referenz ({@link OgnlExpression}), dazu Klassennamen aus
     * {@code new}, {@code instanceof} und {@code @Klasse@member}.
     */
    static PsiReference[] createOgnl(PsiElement element, String expr, int offset) {
        final OgnlExpression ognl = OgnlExpression.parse(expr);
        final OgnlExpression.SyntaxError syntaxError = ognl.syntaxError();
        if (syntaxError != null) return single(new OgnlSyntaxErrorReference(element, syntaxError.range().shiftRight(offset), syntaxError.message()));
        final List<PsiReference> result = new ArrayList<>();
        for (final OgnlExpression.Chain chain : ognl.chains()) {
            OgnlPropertyReference previous = null;
            for (final OgnlExpression.Segment segment : chain.segments()) {
                final String staticClass = previous == null && chain.rootKind() == OgnlExpression.RootKind.STATIC ? chain.staticClass() : null;
                final OgnlPropertyReference reference = new OgnlPropertyReference(element, segment.range().shiftRight(offset),
                        previous, staticClass, segment.call(), segment.indexCount());
                result.add(reference);
                previous = reference;
            }
        }
        for (final OgnlExpression.ClassName className : ognl.classNames()) {
            final JavaClassReferenceProvider provider = new JavaClassReferenceProvider();
            // einfache Namen löst OGNL über java.lang auf – das kann der Provider nicht, daher dort nur weich
            provider.setSoft(!className.name().contains("."));
            result.addAll(List.of(provider.getReferencesByString(className.name(), element, offset + className.range().getStartOffset())));
        }
        result.sort(Comparator.comparingInt(reference -> reference.getRangeInElement().getStartOffset()));
        return result.toArray(PsiReference.EMPTY_ARRAY);
    }

    /**
     * {@code validators:required,email[%email-format],minLength=20[Text],$myValidator} (User's Guide, "Input Validation"):
     * Einträge werden an Kommas außerhalb von {@code [...]} getrennt; {@code $name} verweist auf eine {@code <bean>},
     * eine Meldung {@code [%key]} auf einen Message-Key.
     */
    static PsiReference[] createValidators(PsiElement element, String expr, int offset) {
        final List<PsiReference> result = new ArrayList<>();
        int depth = 0;
        int entryStart = 0;
        for (int i = 0; i <= expr.length(); i++) {
            final char c = i < expr.length() ? expr.charAt(i) : ',';
            if (c == '[') depth++;
            else if (c == ']' && depth > 0) depth--;
            else if (c == ',' && depth == 0) {
                addValidatorReferences(element, expr.substring(entryStart, i), offset + entryStart, result);
                entryStart = i + 1;
            }
        }
        return result.toArray(PsiReference.EMPTY_ARRAY);
    }

    private static void addValidatorReferences(PsiElement element, String entry, int offset, List<PsiReference> result) {
        final int leading = entry.length() - entry.stripLeading().length();
        final int messageStart = entry.indexOf('[');
        final String head = (messageStart >= 0 ? entry.substring(0, messageStart) : entry).trim();
        if (head.startsWith("$") && head.length() > 1) {
            result.add(new SpecChildReference(element, TextRange.from(offset + leading + 1, head.length() - 1), SpecChildReference.Kind.BEAN));
        }
        final int messageEnd = messageStart >= 0 ? entry.indexOf(']', messageStart) : -1;
        if (messageEnd > messageStart + 2 && entry.charAt(messageStart + 1) == '%') {
            result.add(new MessageKeyReference(element, TextRange.create(offset + messageStart + 2, offset + messageEnd)));
        }
    }

    /** Leer (für die Completion) oder ein Java-Bezeichner. */
    private static boolean isSimpleName(String s) {
        return s.isEmpty() || SourceVersion.isIdentifier(s);
    }
}
