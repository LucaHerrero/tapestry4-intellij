package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.SimpleModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.util.PsiModificationTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Zählt nur Änderungen, die die projektweiten Tapestry-Daten betreffen: Inhalt von Spezifikationen
 * (.page/.jwc/.application/.library) und XML-Konfiguration (web.xml, hivemodule.xml) sowie jedes Anlegen, Löschen,
 * Umbenennen oder Verschieben von Dateien. Tippen in Templates oder Java-Dateien lässt den Zähler unverändert –
 * anders als {@link PsiModificationTracker#MODIFICATION_COUNT}, bei dem jeder Tastendruck alle Caches verwirft.
 */
@Service(Service.Level.PROJECT)
public final class TapestryModificationTracker extends SimpleModificationTracker implements Disposable {
    private static final Set<String> TRACKED_EXTENSIONS = Set.of(EXT_PAGE, EXT_COMPONENT, EXT_APPLICATION, EXT_LIBRARY);
    private static final Set<String> TRACKED_NAMES = Set.of(WEB_XML, HIVEMODULE_XML);

    public TapestryModificationTracker(@NotNull Project project) {
        PsiManager.getInstance(project).addPsiTreeChangeListener(new PsiListener(), this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override
            public void after(@NotNull List<? extends VFileEvent> events) {
                for (final VFileEvent event : events) {
                    if (!(event instanceof VFileContentChangeEvent) || isTracked(event.getFile())) {
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

    @Override
    public void dispose() {
    }

    private final class PsiListener extends PsiTreeChangeAdapter {
        private void changed(PsiTreeChangeEvent event) {
            final PsiFile file = event.getFile();
            // ohne Datei: Änderung an der Verzeichnisstruktur (Datei angelegt, gelöscht, verschoben, umbenannt)
            if (file == null || isTracked(file.getViewProvider().getVirtualFile())) incModificationCount();
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

        @Override
        public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
            changed(event);
        }
    }
}
