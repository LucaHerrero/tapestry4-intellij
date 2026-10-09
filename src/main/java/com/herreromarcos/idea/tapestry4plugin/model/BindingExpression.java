package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Zerlegter Binding-Ausdruck {@code prefix:expression}, z.B. {@code ognl:user.name}.
 *
 * @param prefix          bekanntes Präfix oder {@code null}, wenn keines angegeben ist
 * @param expressionStart Offset des Ausdrucks im Originaltext (hinter dem ':' bzw. 0)
 * @param text            der Originaltext
 */
public record BindingExpression(@Nullable String prefix, int expressionStart, @NotNull String text) {

    public static @NotNull BindingExpression parse(@NotNull final String text) {
        final int colon = text.indexOf(':');
        if (colon > 0) {
            final String candidate = text.substring(0, colon).trim();
            if (TapestryConstants.BINDING_PREFIXES.contains(candidate)) {
                return new BindingExpression(candidate, colon + 1, text);
            }
        }
        return new BindingExpression(null, 0, text);
    }

    public @NotNull String expression() {
        return text.substring(expressionStart);
    }

    /** Präfix, oder ersatzweise das Standard-Präfix des Kontexts. */
    public @Nullable String prefixOr(@Nullable final String defaultPrefix) {
        return prefix != null ? prefix : defaultPrefix;
    }

    /** Kein Präfix oder {@code literal:} – der Wert ist ein Literal. */
    public boolean isLiteral() {
        return prefix == null || TapestryConstants.PREFIX_LITERAL.equals(prefix);
    }
}
