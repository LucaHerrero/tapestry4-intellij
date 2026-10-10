package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.JavaClasses;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.META_BEAN_PACKAGES;

/**
 * Einfacher Klassenname in {@code <bean class="Validator">}: wird laut Konfiguration über
 * {@code org.apache.tapestry.bean-class-packages} zu einem vollqualifizierten Namen ergänzt.
 */
public class BeanClassReference extends TapestryReferenceBase {

    public BeanClassReference(@NotNull PsiElement element, @NotNull TextRange range) {
        super(element, range);
    }

    private List<String> packages() {
        return TapestryConfiguration.findMetaList(context(), META_BEAN_PACKAGES);
    }

    @Override
    public @Nullable PsiElement resolve() {
        final String name = getValue().trim();
        for (final String pkg : packages()) {
            final PsiClass cls = JavaClasses.find("%s.%s".formatted(pkg, name), getElement());
            if (cls != null) return cls;
        }
        return JavaClasses.find(name, getElement());
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedSeverity() {
        return HighlightSeverity.WARNING;
    }

    @Override
    public @NotNull String getUnresolvedMessage() {
        final List<String> packages = packages();
        return packages.isEmpty()
                ? "Bean class '%s' not found (no %s configured)".formatted(getValue().trim(), META_BEAN_PACKAGES)
                : "Bean class '%s' not found in %s".formatted(getValue().trim(), String.join(", ", packages));
    }
}
