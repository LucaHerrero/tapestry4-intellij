package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.herreromarcos.idea.tapestry4plugin.model.BindingExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/** Erzeugt die Referenzen für Binding-Ausdrücke ({@code prefix:expression}). */
class BindingReferences {
    private static final Set<String> OGNL_KEYWORDS = Set.of("true", "false", "null", "new", "instanceof", "and", "or", "not",
            "in", "eq", "neq", "lt", "gt", "lte", "gte", "shl", "shr", "ushr", "bor", "xor", "band");

    private BindingReferences() {
    }

    /**
     * @param text          Attributwert (ohne Anführungszeichen)
     * @param offset        Offset von {@code text} innerhalb von {@code element}
     * @param defaultPrefix Präfix, wenn keines angegeben ist ("ognl" in Spezifikationen, {@code null} = Literal im Template)
     */
    static PsiReference @NotNull [] create(@NotNull final PsiElement element, @NotNull final String text, final int offset,
                                           @Nullable final String defaultPrefix) {
        final BindingExpression binding = BindingExpression.parse(text);
        final String prefix = binding.prefixOr(defaultPrefix);
        if (prefix == null) return PsiReference.EMPTY_ARRAY;

        final String expression = binding.expression();
        final int expressionOffset = offset + binding.expressionStart();
        if (PREFIX_OGNL.equals(prefix)) return createOgnl(element, expression, expressionOffset);

        final String trimmed = expression.trim();
        final int leading = expression.length() - expression.stripLeading().length();
        final TextRange range = TextRange.from(expressionOffset + leading, trimmed.length());
        final PsiReference reference = switch (prefix) {
            case PREFIX_LISTENER -> isSimpleName(trimmed) ? new ListenerReference(element, range) : null;
            case PREFIX_MESSAGE -> new MessageKeyReference(element, range);
            case PREFIX_ASSET -> new SpecChildReference(element, range, TAG_ASSET, ATTR_NAME);
            case PREFIX_BEAN -> new SpecChildReference(element, range, TAG_BEAN, ATTR_NAME);
            case PREFIX_COMPONENT -> new ComponentIdReference(element, range, false);
            default -> null;
        };
        return reference != null ? new PsiReference[]{reference} : PsiReference.EMPTY_ARRAY;
    }

    /**
     * Erkennt führende Eigenschaftsketten wie {@code user.address.city} oder {@code items.size()} und
     * erzeugt pro Segment eine Referenz. Komplexere OGNL-Ausdrücke werden ab der ersten unbekannten Stelle ignoriert.
     */
    static PsiReference[] createOgnl(final PsiElement element, final String expr, final int offset) {
        final List<PsiReference> result = new ArrayList<>();
        final int n = expr.length();
        int i = 0;
        while (i < n && Character.isWhitespace(expr.charAt(i))) i++;
        if (i < n && (expr.charAt(i) == '#' || expr.charAt(i) == '@')) return PsiReference.EMPTY_ARRAY;

        OgnlPropertyReference previous = null;
        while (true) {
            final int start = i;
            if (i < n && Character.isJavaIdentifierStart(expr.charAt(i))) {
                while (i < n && Character.isJavaIdentifierPart(expr.charAt(i))) i++;
            }
            final String name = expr.substring(start, i);
            if (name.isEmpty()) {
                // leeres Segment am Ende ("ognl:" oder "user.") → Referenz nur für die Completion
                if (i == n) result.add(new OgnlPropertyReference(element, TextRange.from(offset + start, 0), previous, false));
                break;
            }
            if (previous == null && OGNL_KEYWORDS.contains(name)) break;
            final boolean call = i < n && expr.charAt(i) == '(';
            final OgnlPropertyReference reference = new OgnlPropertyReference(element, TextRange.create(offset + start, offset + i), previous, call);
            result.add(reference);
            previous = reference;
            if (call) {
                i = skipArguments(expr, i);
                if (i < 0) break;
            }
            if (i >= n || expr.charAt(i) != '.') break;
            i++;
        }
        return result.toArray(PsiReference.EMPTY_ARRAY);
    }

    /** @return Index hinter der schließenden Klammer oder -1, wenn sie fehlt. */
    private static int skipArguments(final String expr, final int openParen) {
        int depth = 0;
        for (int i = openParen; i < expr.length(); i++) {
            final char c = expr.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i + 1;
        }
        return -1;
    }

    private static boolean isSimpleName(final String s) {
        if (s.isEmpty()) return true;
        if (!Character.isJavaIdentifierStart(s.charAt(0))) return false;
        for (int i = 1; i < s.length(); i++) {
            if (!Character.isJavaIdentifierPart(s.charAt(i))) return false;
        }
        return true;
    }
}
