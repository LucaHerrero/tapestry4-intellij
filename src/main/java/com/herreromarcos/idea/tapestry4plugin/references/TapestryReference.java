package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.lang.annotation.HighlightSeverity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Marker für Plugin-Referenzen. Alle Referenzen sind "soft"; ob und wie streng ein
 * unaufgelöster Verweis gemeldet wird, entscheidet die Referenz selbst (ausgewertet im Annotator).
 */
public interface TapestryReference {
    /** @return Schweregrad oder {@code null}, wenn nicht gemeldet werden soll (z.B. weil der Kontext unbekannt ist). */
    @Nullable HighlightSeverity getUnresolvedSeverity();

    @NotNull String getUnresolvedMessage();
}
