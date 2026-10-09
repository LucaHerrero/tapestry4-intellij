package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.WEB_INF;
import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.WEB_XML;

/**
 * Auswertung des Deployment-Deskriptors {@code WEB-INF/web.xml}. Elemente werden über den lokalen Namen verglichen:
 * web.xml gibt es mit und ohne Namespace (DTD 2.3 bzw. Schema 2.4+).
 */
public class WebXml {
    private static final String TAG_SERVLET = "servlet";
    private static final String TAG_SERVLET_NAME = "servlet-name";
    private static final String TAG_INIT_PARAM = "init-param";
    private static final String TAG_CONTEXT_PARAM = "context-param";
    private static final String TAG_PARAM_NAME = "param-name";
    private static final String TAG_PARAM_VALUE = "param-value";

    private WebXml() {
    }

    /** Servlet-Namen aus WEB-INF/web.xml – der Namespace-Katalog der Anwendung heißt wie das Servlet. */
    public static @NotNull List<String> servletNames(@NotNull final Project project, @NotNull final VirtualFile webInf) {
        final XmlTag root = rootTag(project, webInf.findChild(WEB_XML));
        final List<String> result = new ArrayList<>();
        for (final XmlTag servlet : children(root, TAG_SERVLET)) {
            final String name = childText(servlet, TAG_SERVLET_NAME);
            if (name != null) result.add(name);
        }
        return result;
    }

    /**
     * Globale Konfiguration in der Reihenfolge, in der Tapestry sie sucht (tapestry.props.GlobalPropertySources):
     * {@code <init-param>} der Servlets, dann {@code <context-param>}. Berücksichtigt werden nur Parameter
     * {@code org.apache.tapestry.*}, damit fremde Servlets nichts beitragen.
     */
    public static @NotNull Map<String, String> tapestryParameters(@NotNull final Project project) {
        final Map<String, String> initParams = new LinkedHashMap<>();
        final Map<String, String> contextParams = new LinkedHashMap<>();
        for (final VirtualFile webXml : FilenameIndex.getVirtualFilesByName(WEB_XML, GlobalSearchScope.projectScope(project))) {
            if (webXml.getParent() == null || !WEB_INF.equals(webXml.getParent().getName())) continue;
            final XmlTag root = rootTag(project, webXml);
            for (final XmlTag servlet : children(root, TAG_SERVLET)) {
                collectParameters(children(servlet, TAG_INIT_PARAM), initParams);
            }
            collectParameters(children(root, TAG_CONTEXT_PARAM), contextParams);
        }
        final Map<String, String> result = new LinkedHashMap<>(initParams);
        contextParams.forEach(result::putIfAbsent);
        return result;
    }

    private static void collectParameters(final List<XmlTag> parameters, final Map<String, String> out) {
        for (final XmlTag parameter : parameters) {
            final String name = childText(parameter, TAG_PARAM_NAME);
            final String value = childText(parameter, TAG_PARAM_VALUE);
            if (name != null && value != null && name.startsWith("org.apache.tapestry.")) out.putIfAbsent(name, value);
        }
    }

    private static @Nullable XmlTag rootTag(final Project project, @Nullable final VirtualFile webXml) {
        return webXml != null ? SpecXml.rootTag(project, webXml) : null;
    }

    private static List<XmlTag> children(@Nullable final XmlTag parent, final String localName) {
        if (parent == null) return List.of();
        final List<XmlTag> result = new ArrayList<>();
        for (final XmlTag child : parent.getSubTags()) {
            if (localName.equals(child.getLocalName())) result.add(child);
        }
        return result;
    }

    private static @Nullable String childText(final XmlTag parent, final String localName) {
        for (final XmlTag child : children(parent, localName)) {
            final String text = child.getValue().getTrimmedText();
            if (!text.isEmpty()) return text;
        }
        return null;
    }
}
