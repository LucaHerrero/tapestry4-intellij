package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;

/** .page, .jwc, .application, .library und .script sind nur dann XML, wenn sie wirklich Tapestry-Spezifikationen sind. */
public class FileTypeDetectionTest extends TapestryTestCase {

    public void testSpecificationWithDoctypeIsXml() {
        assertXml("Doc.page", DOCTYPE_4_0.formatted("page-specification") + "<page-specification/>");
    }

    public void testSpecificationWithoutDoctypeIsXml() {
        assertXml("Plain.jwc", "<component-specification/>");
        assertXml("Plain.application", "<?xml version=\"1.0\"?>\n<application name=\"App\"/>");
        assertXml("Plain.library", "<library-specification/>");
        assertXml("Plain.script", "<script><body/></script>");
    }

    public void testLicenseHeaderBeforeRootIsSkipped() {
        assertXml("Licensed.page", """
                <?xml version="1.0"?>
                <!-- <component-specification> im Kommentar zählt nicht -->
                <page-specification/>
                """);
    }

    public void testForeignContentKeepsItsType() {
        assertNotXml("Notes.page", "Das ist keine Spezifikation.");
        assertNotXml("deploy.script", "#!/bin/sh\necho hallo\n");
        assertNotXml("Other.page", "<html><body/></html>");
        assertNotXml("Wrong.jwc", "<page-specification/>");
    }

    private void assertXml(String path, String text) {
        assertEquals(path, XmlFileType.INSTANCE, fileType(path, text));
    }

    private void assertNotXml(String path, String text) {
        assertNotSame(path, XmlFileType.INSTANCE, fileType(path, text));
    }

    private FileType fileType(String path, String text) {
        final PsiFile file = myFixture.addFileToProject("detect/" + path, text);
        return file.getVirtualFile().getFileType();
    }
}
