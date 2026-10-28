package com.herreromarcos.idea.tapestry4plugin.model;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.DefaultFileTypeSpecificInputFilter;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.ID;
import com.intellij.util.indexing.ScalarIndexExtension;
import com.intellij.util.io.EnumeratorStringDescriptor;
import com.intellij.util.io.KeyDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import static com.herreromarcos.idea.tapestry4plugin.TapestryConstants.*;

/**
 * Index {@code class}-Attribut der Seiten- und Komponentenspezifikationen (.page/.jwc) → Datei. Ersetzt das Einlesen
 * aller Spezifikationen nach jeder Änderung: IntelliJ indiziert nur geänderte Dateien neu, die Suche nach den
 * Spezifikationen einer Klasse ist ein direkter Zugriff über den Klassennamen.
 */
public class SpecClassIndex extends ScalarIndexExtension<String> {
    public static final ID<String, Void> NAME = ID.create("tapestry4.specClass");
    private static final Set<String> EXTENSIONS = Set.of(EXT_PAGE, EXT_COMPONENT);
    private static final Set<String> ROOTS = Set.of(ROOT_PAGE, ROOT_COMPONENT);

    @Override
    public @NotNull ID<String, Void> getName() {
        return NAME;
    }

    @Override
    public @NotNull DataIndexer<String, Void, FileContent> getIndexer() {
        return input -> {
            final XmlTag root = input.getPsiFile() instanceof final XmlFile xml ? xml.getRootTag() : null;
            final String className = root != null && ROOTS.contains(root.getName()) ? SpecXml.attr(root, ATTR_CLASS) : null;
            return className != null ? Collections.singletonMap(className, null) : Map.of();
        };
    }

    @Override
    public @NotNull KeyDescriptor<String> getKeyDescriptor() {
        return EnumeratorStringDescriptor.INSTANCE;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public FileBasedIndex.@NotNull InputFilter getInputFilter() {
        return new DefaultFileTypeSpecificInputFilter(XmlFileType.INSTANCE) {
            @Override
            public boolean acceptInput(@NotNull VirtualFile file) {
                return file.getExtension() != null && EXTENSIONS.contains(file.getExtension());
            }
        };
    }

    @Override
    public boolean dependsOnFileContent() {
        return true;
    }
}
