package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.herreromarcos.idea.tapestry4plugin.model.ApplicationStateObjects;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Name eines Application State Objects: {@code state:visit}, {@code <inject type="state|state-flag" object="visit">},
 * {@code @InjectState("visit")}, {@code @InjectStateFlag("visit")} → {@code <state-object name>} in hivemodule.xml.
 */
public class StateObjectReference extends TapestryReferenceBase {

    public StateObjectReference(@NotNull final PsiElement element, @NotNull final TextRange range) {
        super(element, range);
    }

    private Map<String, XmlAttributeValue> stateObjects() {
        return ApplicationStateObjects.getAll(getElement().getProject());
    }

    @Override
    public @Nullable PsiElement resolve() {
        return stateObjects().get(getValue().trim());
    }

    @Override
    public Object @NotNull [] getVariants() {
        return stateObjects().entrySet().stream()
                .map(entry -> LookupElementBuilder.create(entry.getKey()).withIcon(TapestryIcons.TAPESTRY)
                        .withTypeText(scope(entry.getValue()), true))
                .toArray();
    }

    private static String scope(final XmlAttributeValue name) {
        final XmlTag stateObject = PsiTreeUtil.getParentOfType(name, XmlTag.class);
        final String scope = stateObject != null ? stateObject.getAttributeValue("scope") : null;
        return scope != null ? scope : "";
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        // Ohne gefundene Moduldeskriptoren (Tapestry-JAR fehlt im Classpath) lässt sich nichts prüfen
        return stateObjects().isEmpty() ? null : HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        return "Unknown application state object '%s'".formatted(getValue().trim());
    }
}
