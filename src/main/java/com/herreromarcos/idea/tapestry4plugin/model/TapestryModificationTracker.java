package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Zählt nur Änderungen, die die projektweiten Tapestry-Daten betreffen: Inhalt von Spezifikationen
 * (.page/.jwc/.application/.library) und XML-Konfiguration (web.xml, hivemodule.xml) sowie Anlegen, Löschen,
 * Umbenennen oder Verschieben von Verzeichnissen und von Dateien, die das Plugin auswertet (Spezifikationen,
 * Templates, Kataloge, Java-Klassen). Tippen in Templates oder Java-Dateien und das Anlegen sonstiger Dateien
 * (z.B. Compiler-Ausgaben) lassen den Zähler unverändert – anders als {@link PsiModificationTracker#MODIFICATION_COUNT},
 * bei dem jeder Tastendruck alle Caches verwirft.
 */
@Service(Service.Level.PROJECT)
public final class TapestryModificationTracker extends SimpleModificationTracker implements Disposable {
    private static final Set<String> TRACKED_EXTENSIONS = Set.of(EXT_PAGE, EXT_COMPONENT, EXT_APPLICATION, EXT_LIBRARY);
    private static final Set<String> TRACKED_NAMES = Set.of(WEB_XML, HIVEMODULE_XML);
    /**
     * Endungen, deren Anlegen, Löschen oder Umbenennen die Verzeichnisse ändert; Templates mit eigener Endung sind
     * als HTML zugeordnet und werden über den Dateityp erkannt.
     */
    private static final Set<String> STRUCTURE_EXTENSIONS = Set.of(EXT_PAGE, EXT_COMPONENT, EXT_APPLICATION, EXT_LIBRARY,
            EXT_SCRIPT, TEMPLATE_EXT, "htm", "xhtml", "properties", "java");

    public TapestryModificationTracker(@NotNull Project project) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiListener(), this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (final VFileEvent event : events) {
                    if (isRelevant(event)) {
                        incModificationCount();
                        return;
                    }
                }
            }
        });
    }

    public static @NotNull TapestryModificationTracker getInstance(@NotNull Project project) {
        return project.getService(TapestryModificationTracker.class);
    }

    /** Abhängigkeiten projektweiter Daten aus Spezifikationen und Konfiguration. */
    public static Object @NotNull [] dependencies(@NotNull Project project) {
        return new Object[]{getInstance(project), ProjectRootModificationTracker.getInstance(project)};
    }

    /** Wie {@link #dependencies}, zusätzlich Java-Änderungen (Klassen, {@code @Meta}, {@code @Component}, {@code @Parameter}). */
    public static Object @NotNull [] dependenciesWithJava(@NotNull Project project) {
        return new Object[]{getInstance(project), ProjectRootModificationTracker.getInstance(project),
                PsiModificationTracker.getInstance(project).forLanguage(JavaLanguage.INSTANCE)};
    }

    private static boolean isTracked(@Nullable VirtualFile file) {
        return file != null && (TRACKED_NAMES.contains(file.getName())
                || file.getExtension() != null && TRACKED_EXTENSIONS.contains(file.getExtension()));
    }

    /** Inhaltsänderung einer ausgewerteten Konfigurationsdatei oder Strukturänderung einer relevanten Datei. */
    private static boolean isRelevant(VFileEvent event) {
        return switch (event) {
            case final VFileContentChangeEvent change -> isTracked(change.getFile());
            case final VFileCreateEvent create -> create.isDirectory() || affectsStructure(create.getChildName());
            case final VFileCopyEvent copy -> affectsStructure(copy.getFile()) || affectsStructure(copy.getNewChildName());
            case final VFilePropertyChangeEvent property -> property.isRename()
                    && (affectsStructure(property.getFile()) || affectsStructure(String.valueOf(property.getNewValue())));
            // Löschen und Verschieben
            default -> affectsStructure(event.getFile());
        };
    }

    private static boolean affectsStructure(@Nullable VirtualFile file) {
        return file == null || file.isDirectory() || affectsStructure(file.getName());
    }

    private static boolean affectsStructure(@NotNull String fileName) {
        final String extension = FileUtilRt.getExtension(fileName).toLowerCase(Locale.ROOT);
        return STRUCTURE_EXTENSIONS.contains(extension) || TRACKED_NAMES.contains(fileName)
                || FileTypeRegistry.getInstance().getFileTypeByFileName(fileName) instanceof HtmlFileType;
    }

    @Override
    public void dispose() {
    }

    private final class PsiListener extends PsiTreeChangeAdapter {
        private void changed(PsiTreeChangeEvent event) {
            final PsiFile file = event.getFile();
            if (file != null) {
                if (isTracked(file.getViewProvider().getVirtualFile())) incModificationCount();
                return;
            }
            // ohne Datei: Änderung an der Verzeichnisstruktur (Datei angelegt, gelöscht, verschoben, umbenannt)
            final PsiElement[] elements = {event.getChild(), event.getOldChild(), event.getNewChild(), event.getElement()};
            boolean known = false;
            for (final PsiElement element : elements) {
                if (element instanceof PsiDirectory) {
                    incModificationCount();
                    return;
                }
                if (element instanceof final PsiFile changedFile) {
                    known = true;
                    if (affectsStructure(changedFile.getName())
                            || event.getOldValue() instanceof final String oldName && affectsStructure(oldName)) {
                        incModificationCount();
                        return;
                    }
                }
            }
            if (!known) incModificationCount();
        }

        @Override
        public void childAdded(@NotNull PsiTreeChangeEvent event) {
            changed(event);
        }

        @Override
        public void childRemoved(@NotNull PsiTreeChangeEvent event) {
            changed(event);
        }

        @Override
        public void childReplaced(@NotNull PsiTreeChangeEvent event) {
            changed(event);
        }

        @Override
        public void childMoved(@NotNull PsiTreeChangeEvent event) {
            changed(event);
        }

        @Override
        public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
            changed(event);
        }

        /**
         * Nur Umbenennungen. Andere Eigenschaften (entladene PSI, Schreibschutz, neu erkannte Dateitypen) ändern keine
         * projektweiten Daten: die Verzeichnisse arbeiten mit Endungen, Werte einzelner Dateien hängen an deren PSI.
         */
        @Override
        public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
            final String property = event.getPropertyName();
            if (PsiTreeChangeEvent.PROP_FILE_NAME.equals(property) || PsiTreeChangeEvent.PROP_DIRECTORY_NAME.equals(property)) {
                changed(event);
            }
        }
    }
}
