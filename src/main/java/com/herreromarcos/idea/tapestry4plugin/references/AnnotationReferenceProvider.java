package com.herreromarcos.idea.tapestry4plugin.references;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryConfiguration;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryContext;
import com.herreromarcos.idea.tapestry4plugin.model.TapestryModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;
import static com.herreromarcos.idea.tapestry4plugin.references.TapestryReferenceBase.single;

/**
 * Referenzen in String-Werten der Tapestry-Annotationen (Annotations-Referenz):
 * <ul>
 *   <li>{@code @InjectPage("Details")} → Seite</li>
 *   <li>{@code @InjectComponent("inputUserName")}, {@code @Component(copyOf = "...")} → deklarierte Komponente</li>
 *   <li>{@code @Component(type = "TextField")} → Komponententyp, {@code bindings = {"value=ognl:x"}} → Binding-Ausdruck</li>
 *   <li>{@code @InjectAsset("stylesheet")} → Asset, {@code @Message("page-title")} → Message-Key</li>
 *   <li>{@code @InjectState("visit")}, {@code @InjectStateFlag("visit")} → Application State Object</li>
 *   <li>{@code @InitialValue("request.serverName")} → Binding-Ausdruck (Standard OGNL)</li>
 *   <li>{@code @InjectScript("x.script")}, {@code @Asset("/style.css")} → Datei relativ zu Spezifikation bzw. Template</li>
 * </ul>
 */
class AnnotationReferenceProvider extends PsiReferenceProvider {

    @Override
    public PsiReference @NotNull [] getReferencesByElement(@NotNull final PsiElement element, @NotNull final ProcessingContext context) {
        if (!(element instanceof final PsiLiteralExpression literal) || !(literal.getValue() instanceof String)) {
            return PsiReference.EMPTY_ARRAY;
        }
        final PsiNameValuePair pair = PsiTreeUtil.getParentOfType(literal, PsiNameValuePair.class);
        final PsiAnnotation annotation = PsiTreeUtil.getParentOfType(pair, PsiAnnotation.class);
        final String qualifiedName = annotation != null ? annotation.getQualifiedName() : null;
        if (qualifiedName == null || !qualifiedName.startsWith(ANNOTATIONS)) return PsiReference.EMPTY_ARRAY;

        final String attribute = pair.getAttributeName();
        final String text = ElementManipulators.getValueText(literal);
        final TextRange range = ElementManipulators.getValueTextRange(literal);
        final int offset = range.getStartOffset();
        return switch ("%s#%s".formatted(StringUtils.removeStart(qualifiedName, ANNOTATIONS), attribute)) {
            case "InjectPage#value" -> single(new PageReference(literal, range));
            case "InjectComponent#value", "Component#copyOf" -> single(new ComponentIdReference(literal, range, false));
            case "Component#type" -> single(new ComponentTypeReference(literal, range));
            case "Component#bindings" -> componentBinding(literal, text, offset);
            case "InjectAsset#value" -> single(new SpecChildReference(literal, range, SpecChildReference.Kind.ASSET));
            case "Message#value" -> single(new MessageKeyReference(literal, range));
            case "InjectState#value", "InjectStateFlag#value" -> single(new StateObjectReference(literal, range));
            case "InitialValue#value" -> BindingReferences.create(literal, text, offset, defaultPrefix(literal));
            case "InjectScript#value", "Asset#value" -> SpecPathReferences.create(literal, text, offset, true, baseFile(literal));
            default -> PsiReference.EMPTY_ARRAY;
        };
    }

    /** {@code "value=ognl:user.name"}: nur der Teil hinter dem '=' ist ein Binding-Ausdruck. */
    private static PsiReference[] componentBinding(final PsiLiteralExpression literal, final String text, final int offset) {
        final int separator = text.indexOf('=');
        if (separator < 0) return PsiReference.EMPTY_ARRAY;
        return BindingReferences.create(literal, text.substring(separator + 1), offset + separator + 1, defaultPrefix(literal));
    }

    /** In Annotationen gilt wie in Spezifikationen OGNL als Standard (bzw. das konfigurierte default-binding-prefix). */
    private static String defaultPrefix(final PsiElement element) {
        final TapestryContext ctx = context(element);
        return ctx != null && ctx.spec() != null ? TapestryConfiguration.getDefaultBindingPrefix(ctx.spec()) : PREFIX_OGNL;
    }

    /** Pfade sind relativ zur Spezifikation, ohne Spezifikation relativ zum Template. */
    private static @Nullable VirtualFile baseFile(final PsiElement element) {
        final TapestryContext ctx = context(element);
        final PsiFile owner = ctx == null ? null : ctx.spec() != null ? ctx.spec() : ctx.template();
        return owner != null ? owner.getOriginalFile().getVirtualFile() : null;
    }

    private static @Nullable TapestryContext context(final PsiElement element) {
        final PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
        return psiClass != null ? TapestryModel.getContext(psiClass) : null;
    }
}
