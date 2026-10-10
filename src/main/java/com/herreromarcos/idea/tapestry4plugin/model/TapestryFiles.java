package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.lang.Language;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.lang.xhtml.XHTMLLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

/** Klassifikation von Dateien: Tapestry-Spezifikation (.page/.jwc/.application/.library) oder HTML-Template. */
public class TapestryFiles {
    private TapestryFiles() {
    }

    /**
     * Art der Spezifikation – primär über das Wurzel-Tag, ersatzweise über die Dateiendung.
     * Das Wurzel-Tag {@code <application>} ist mehrdeutig (Java EE application.xml) und zählt nur mit
     * Endung .application oder Tapestry-DOCTYPE.
     */
    public static @Nullable SpecKind getSpecKind(@Nullable PsiFile file) {
        if (!(file instanceof final XmlFile xmlFile)) return null;
        final VirtualFile vf = file.getOriginalFile().getVirtualFile();
        final SpecKind byExtension = vf != null ? SpecKind.byExtension(vf.getExtension()) : null;
        final XmlTag root = xmlFile.getRootTag();
        final SpecKind byRoot = root != null ? SpecKind.byRootTag(root.getName()) : null;
        if (byRoot == SpecKind.APPLICATION && byExtension != SpecKind.APPLICATION && !hasTapestryDoctype(xmlFile)) {
            return null;
        }
        return byRoot != null ? byRoot : byExtension;
    }

    private static boolean hasTapestryDoctype(XmlFile file) {
        final XmlDocument document = file.getDocument();
        final XmlProlog prolog = document != null ? document.getProlog() : null;
        final XmlDoctype doctype = prolog != null ? prolog.getDoctype() : null;
        final String publicId = doctype != null ? doctype.getPublicId() : null;
        return publicId != null && publicId.contains("Tapestry");
    }

    public static boolean isSpecFile(@Nullable PsiFile file) {
        return file instanceof XmlFile && !isTemplateFile(file) && getSpecKind(file) != null;
    }

    public static boolean isTemplateFile(@Nullable PsiFile file) {
        if (!(file instanceof XmlFile)) return false;
        final Language language = file.getViewProvider().getBaseLanguage();
        return language.isKindOf(HTMLLanguage.INSTANCE) || language.isKindOf(XHTMLLanguage.INSTANCE);
    }
}
