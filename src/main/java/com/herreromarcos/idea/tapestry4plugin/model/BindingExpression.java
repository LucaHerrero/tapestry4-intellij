package com.herreromarcos.idea.tapestry4plugin.model;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegter Binding-Ausdruck {@code prefix:expression}, z.B. {@code ognl:user.name}.
 *
 * <p>Wie in Tapestrys BindingSourceImpl gilt der Teil vor dem ersten Doppelpunkt nur dann als Präfix, wenn er
 * registriert ist (tapestry.bindings.BindingFactories). Sonst wird der <em>ganze</em> Wert mit dem Standard-Präfix
 * ausgewertet – im Template als Literal ({@code http://example.com}), in Spezifikationen als OGNL.
 *
 * @param prefix             dokumentiertes Präfix oder {@code null}
 * @param customPrefix       registriertes projekteigenes Präfix oder {@code null}; sein Ausdruck wird nicht ausgewertet
 * @param unregisteredPrefix {@code name:} ohne Registrierung (gehört zum Ausdruck) oder {@code null}
 * @param expressionStart    Offset des Ausdrucks im Originaltext (hinter einem registrierten Präfix, sonst 0)
 * @param text               der Originaltext
 */
public record BindingExpression(@Nullable String prefix, @Nullable String customPrefix, @Nullable String unregisteredPrefix,
                                int expressionStart, @NotNull String text) {
    private static final Pattern PREFIX = Pattern.compile("^\\s*([A-Za-z][A-Za-z0-9_-]*):");

    /** Zerlegung nur mit den dokumentierten Präfixen. */
    public static @NotNull BindingExpression parse(@NotNull final String text) {
        return parse(text, Set.of());
    }

    /** Zerlegung mit den dokumentierten und den im Projekt registrierten Präfixen. */
    public static @NotNull BindingExpression parse(@NotNull final String text, @NotNull final PsiElement context) {
        return parse(text, BindingPrefixes.getCustom(context.getProject()));
    }

    public static @NotNull BindingExpression parse(@NotNull final String text, @NotNull final Set<String> customPrefixes) {
        final Matcher matcher = PREFIX.matcher(text);
        if (!matcher.find()) return new BindingExpression(null, null, null, 0, text);
        final String candidate = matcher.group(1);
        if (TapestryConstants.BINDING_PREFIXES.contains(candidate)) {
            return new BindingExpression(candidate, null, null, matcher.end(), text);
        }
        if (customPrefixes.contains(candidate)) {
            return new BindingExpression(null, candidate, null, matcher.end(), text);
        }
        return new BindingExpression(null, null, candidate, 0, text);
    }

    public @NotNull String expression() {
        return text.substring(expressionStart);
    }

    /** Effektives, auswertbares Präfix: das dokumentierte Präfix, sonst das Standard-Präfix – bei projekteigenem keines. */
    public @Nullable String effectivePrefix(@Nullable final String defaultPrefix) {
        if (customPrefix != null) return null;
        return prefix != null ? prefix : defaultPrefix;
    }

    /** Das angegebene registrierte Präfix (dokumentiert oder projekteigen) oder {@code null}. */
    public @Nullable String registeredPrefix() {
        return prefix != null ? prefix : customPrefix;
    }

    /** Kein registriertes Präfix oder {@code literal:} – im Template ist der Wert dann ein Literal. */
    public boolean isLiteral() {
        return registeredPrefix() == null || TapestryConstants.PREFIX_LITERAL.equals(prefix);
    }
}
