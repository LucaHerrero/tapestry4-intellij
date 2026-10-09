package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Zerlegter Wert eines {@code jwcid}-Attributs.
 * <ul>
 *   <li>{@code "foo"} – Verweis auf eine deklarierte Komponente ({@link #id()} = foo, kein Typ)</li>
 *   <li>{@code "@Insert"} – implizite, anonyme Komponente ({@link #type()} = Insert)</li>
 *   <li>{@code "foo@Insert"} – implizite Komponente mit eigener id</li>
 *   <li>{@code "$content$"}, {@code "$remove$"} – Sonder-ids ({@link #special()})</li>
 * </ul>
 *
 * @param id        id-Anteil oder {@code null}
 * @param type      Typ-Anteil nach '@' oder {@code null}
 * @param special   Sonder-id ($content$/$remove$)
 * @param idRange   Bereich der id im Originaltext (leer, wenn keine id)
 * @param typeRange Bereich des Typs im Originaltext (nur gültig, wenn ein '@' vorhanden ist)
 */
public record Jwcid(@Nullable String id, @Nullable String type, boolean special,
                    @NotNull TextRange idRange, @NotNull TextRange typeRange) {

    public static @NotNull Jwcid parse(@NotNull final String text) {
        final String trimmed = text.trim();
        final int start = trimmed.isEmpty() ? 0 : text.indexOf(trimmed);
        final int end = start + trimmed.length();
        if (trimmed.startsWith("$")) {
            return new Jwcid(null, null, true, TextRange.EMPTY_RANGE, TextRange.EMPTY_RANGE);
        }
        final int at = trimmed.indexOf('@');
        if (at < 0) {
            return new Jwcid(trimmed, null, false, TextRange.create(start, end), TextRange.EMPTY_RANGE);
        }
        final String id = trimmed.substring(0, at).trim();
        return new Jwcid(id.isEmpty() ? null : id, trimmed.substring(at + 1).trim(), false,
                TextRange.create(start, start + at), TextRange.create(start + at + 1, end));
    }

    /** {@code jwcid="foo"} – verweist auf eine in Spezifikation/Klasse deklarierte Komponente. */
    public boolean isDeclaredReference() {
        return !special && type == null;
    }

    /** {@code jwcid="@Type"} bzw. {@code "id@Type"}. */
    public boolean isImplicit() {
        return type != null;
    }
}
