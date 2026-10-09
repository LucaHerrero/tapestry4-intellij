package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEnumerator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.WEB_INF;

/** Pfadauflösung: specification-path, Web-Root, Classpath und Ähnlichkeit von Dateipfaden. */
public class TapestryPaths {
    private TapestryPaths() {
    }

    /**
     * Löst einen specification-path auf: relativ zur Datei, oder absolut ("/...") gegen Web-Root und Classpath.
     * Präfixe "classpath:" und "context:" werden ignoriert.
     */
    public static @Nullable VirtualFile resolve(@NotNull final Project project, @NotNull final String specificationPath,
                                                @NotNull final VirtualFile relativeTo) {
        final String path = StringUtils.removeStart(StringUtils.removeStart(specificationPath.trim(), "classpath:"), "context:");
        if (!path.startsWith("/")) {
            final VirtualFile parent = relativeTo.getParent();
            return parent != null ? parent.findFileByRelativePath(path) : null;
        }
        final String relative = path.substring(1);
        final List<VirtualFile> roots = new ArrayList<>();
        final VirtualFile webRoot = webRoot(relativeTo);
        if (webRoot != null) roots.add(webRoot);
        roots.addAll(classpathRoots(project));
        roots.add(VfsUtilCore.getRootFile(relativeTo));
        for (final VirtualFile root : roots) {
            final VirtualFile found = root.findFileByRelativePath(relative);
            if (found != null) return found;
        }
        return null;
    }

    /** Quell- und Klassen-Wurzeln aller Module und Bibliotheken (ohne JDK). */
    public static @NotNull List<VirtualFile> classpathRoots(@NotNull final Project project) {
        final List<VirtualFile> roots = new ArrayList<>();
        final OrderEnumerator enumerator = OrderEnumerator.orderEntries(project).withoutSdk();
        Collections.addAll(roots, enumerator.getAllSourceRoots());
        Collections.addAll(roots, enumerator.getClassesRoots());
        return roots;
    }

    /** Das Web-Root (Verzeichnis, das WEB-INF enthält) für eine Datei. */
    public static @Nullable VirtualFile webRoot(@NotNull final VirtualFile file) {
        for (VirtualFile dir = file.isDirectory() ? file : file.getParent(); dir != null; dir = dir.getParent()) {
            if (WEB_INF.equals(dir.getName())) return dir.getParent();
            final VirtualFile webInf = dir.findChild(WEB_INF);
            if (webInf != null && webInf.isDirectory()) return dir;
        }
        return null;
    }

    /** Pfad relativ zu WEB-INF ohne Endung, z.B. "admin/Users" – oder {@code null}, wenn nicht unter WEB-INF. */
    static @Nullable String pathBelowWebInf(@NotNull final VirtualFile file) {
        for (VirtualFile dir = file.getParent(); dir != null; dir = dir.getParent()) {
            if (WEB_INF.equals(dir.getName())) {
                final String relative = VfsUtilCore.getRelativePath(file, dir, '/');
                return relative != null ? StringUtil.trimEnd(relative, "." + file.getExtension()) : null;
            }
        }
        return null;
    }

    /**
     * Logischer Seitenname mit Ordnern, z.B. {@code admin/EditUser}: relativ zu WEB-INF (Spezifikationen)
     * bzw. zum Web-Root (Templates), ohne Endung; {@code null} außerhalb einer Web-Anwendung.
     */
    static @Nullable String logicalPagePath(@NotNull final VirtualFile file) {
        final String belowWebInf = pathBelowWebInf(file);
        if (belowWebInf != null) return belowWebInf;
        final VirtualFile webRoot = webRoot(file);
        final String relative = webRoot != null ? VfsUtilCore.getRelativePath(file, webRoot, '/') : null;
        return relative != null ? StringUtil.trimEnd(relative, "." + file.getExtension()) : null;
    }

    /**
     * Optionales Locale-Suffix im Dateinamen ({@code _de}, {@code _de_AT}, {@code _en_US_POSIX}) – nicht aber
     * {@code _Admin}, das wäre ein anderer Name.
     */
    public static final String LOCALE_SUFFIX = "(_[a-z]{2,3}(_([A-Z]{2}|[0-9]{3})(_\\w+)?)?)";
    private static final Pattern LOCALIZED_NAME = Pattern.compile("(.+?)" + LOCALE_SUFFIX);

    /** {@code Home_de} → {@code Home}; Namen ohne Locale-Suffix bleiben unverändert. */
    public static @NotNull String stripLocale(@NotNull final String baseName) {
        final Matcher matcher = LOCALIZED_NAME.matcher(baseName);
        return matcher.matches() ? matcher.group(1) : baseName;
    }

    /** Kandidat mit dem zur Referenz ähnlichsten Pfad. */
    static @Nullable VirtualFile closest(@NotNull final VirtualFile reference, @NotNull final Collection<VirtualFile> candidates) {
        return candidates.stream().max(Comparator.comparingInt(c -> similarity(reference, c))).orElse(null);
    }

    /** Alle Kandidaten mit maximaler Pfadähnlichkeit zur Referenz. */
    static @NotNull List<VirtualFile> allClosest(@NotNull final VirtualFile reference, @NotNull final Collection<VirtualFile> candidates) {
        final int best = candidates.stream().mapToInt(c -> similarity(reference, c)).max().orElse(0);
        return candidates.stream().filter(c -> similarity(reference, c) == best).toList();
    }

    /** Ähnlichkeit zweier Dateipfade; gleiches Verzeichnis gewinnt, WEB-INF wird ignoriert. */
    static int similarity(@NotNull final VirtualFile a, @NotNull final VirtualFile b) {
        if (Objects.equals(a.getParent(), b.getParent())) return 100_000;
        final List<String> pa = dirSegments(a);
        final List<String> pb = dirSegments(b);
        int suffix = 0;
        while (suffix < pa.size() && suffix < pb.size()
                && pa.get(pa.size() - 1 - suffix).equals(pb.get(pb.size() - 1 - suffix))) {
            suffix++;
        }
        int prefix = 0;
        while (prefix < pa.size() && prefix < pb.size() && pa.get(prefix).equals(pb.get(prefix))) {
            prefix++;
        }
        return suffix * 1000 + prefix;
    }

    private static List<String> dirSegments(final VirtualFile file) {
        final LinkedList<String> segments = new LinkedList<>();
        for (VirtualFile dir = file.getParent(); dir != null; dir = dir.getParent()) {
            if (!WEB_INF.equals(dir.getName())) segments.addFirst(dir.getName());
        }
        return segments;
    }
}
