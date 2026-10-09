package com.herreromarcos.idea.tapestry4plugin.references;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * Findet OGNL-Referenzen auf Getter: im Template steht {@code ognl:userName}, die Java-Suche sucht aber nach
 * dem Wort {@code getUserName}. Registriert für {@link MethodReferencesSearch}, das sowohl Find Usages
 * als auch Rename von Methoden verwenden.
 */
public class OgnlPropertyReferenceSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

    public OgnlPropertyReferenceSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull final MethodReferencesSearch.SearchParameters parameters, @NotNull final Processor<? super PsiReference> consumer) {
        final PsiMethod method = parameters.getMethod();
        if (!PropertyUtilBase.isSimplePropertyGetter(method)) return;
        final String propertyName = PropertyUtilBase.getPropertyName(method);
        if (propertyName == null || propertyName.equals(method.getName())) return;
        parameters.getOptimizer().searchWord(propertyName, parameters.getEffectiveSearchScope(), UsageSearchContext.ANY, true, method);
    }
}
