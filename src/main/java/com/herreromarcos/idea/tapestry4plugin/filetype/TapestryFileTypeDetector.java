package com.herreromarcos.idea.tapestry4plugin.filetype;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.ByteSequence;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Erkennt Tapestry-Spezifikationen (.page, .jwc, .application, .library, .script) am Inhalt und behandelt nur sie als XML.
 * Die Endungen sind zu allgemein, um sie global auf XML umzubiegen: Eine .page oder .script aus einem anderen Kontext
 * bleibt, was sie ist. Detektoren greifen ohnehin nur, wenn keine andere Zuordnung über den Dateinamen existiert.
 */
public class TapestryFileTypeDetector implements FileTypeRegistry.FileTypeDetector {
    /** Endung → erwartetes Wurzel-Element (Tapestry 3.0, 4.0 und 4.1 verwenden dieselben). */
    private static final Map<String, String> ROOT_BY_EXTENSION = Map.of(
            TapestryConstants.EXT_PAGE, TapestryConstants.ROOT_PAGE,
            TapestryConstants.EXT_COMPONENT, TapestryConstants.ROOT_COMPONENT,
            TapestryConstants.EXT_APPLICATION, TapestryConstants.ROOT_APPLICATION,
            TapestryConstants.EXT_LIBRARY, TapestryConstants.ROOT_LIBRARY,
            TapestryConstants.EXT_SCRIPT, TapestryConstants.ROOT_SCRIPT);
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    /** Erstes Element; {@code <?xml} und {@code <!DOCTYPE} beginnen nicht mit einem Buchstaben. */
    private static final Pattern FIRST_ELEMENT = Pattern.compile("<([A-Za-z][\\w.:-]*)");
    /** Reicht auch für Spezifikationen mit Lizenzkopf vor dem Wurzel-Element. */
    private static final int PREFIX_LENGTH = 8192;

    @Override
    public @Nullable FileType detect(@NotNull VirtualFile file, @NotNull ByteSequence firstBytes, @Nullable CharSequence firstCharsIfText) {
        final String expectedRoot = file.getExtension() != null ? ROOT_BY_EXTENSION.get(file.getExtension().toLowerCase()) : null;
        if (expectedRoot == null || firstCharsIfText == null) return null;
        final String text = firstCharsIfText.toString().stripLeading();
        if (!text.startsWith("<")) return null;
        final Matcher root = FIRST_ELEMENT.matcher(COMMENT.matcher(text).replaceAll(""));
        return root.find() && expectedRoot.equals(root.group(1)) ? XmlFileType.INSTANCE : null;
    }

    @Override
    public int getDesiredContentPrefixLength() {
        return PREFIX_LENGTH;
    }
}
