package com.herreromarcos.idea.tapestry4plugin;

import com.herreromarcos.idea.tapestry4plugin.model.TapestryModificationTracker;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;

/**
 * Behobene Probleme müssen sofort verschwinden: die Caches hängen nicht mehr an jeder PSI-Änderung, dürfen aber
 * Änderungen an Spezifikationen und Java-Klassen nicht verpassen.
 */
public class CacheInvalidationTest extends TapestryTestCase {

    public void testComponentDeclaredInSpecIsRecognizedImmediately() {
        configure("Home.html", "<html><body><span jwcid=\"farewell\"/></body></html>");
        assertTrue(errors().toString(), errors().stream().anyMatch(m -> m.contains("'farewell' is not declared")));

        final PsiFile spec = getPsiManager().findFile(myFixture.findFileInTempDir("Home.page"));
        assertNotNull(spec);
        replace(spec, "<asset name=\"logo\"", "<component id=\"farewell\" type=\"Any\"/>\n  <asset name=\"logo\"");

        assertFalse(errors().toString(), errors().stream().anyMatch(m -> m.contains("'farewell' is not declared")));
    }

    public void testGetterAddedInClassIsRecognizedImmediately() {
        configure("Home.html", "<html><body><span jwcid=\"@Insert\" value=\"ognl:email\"/></body></html>");
        assertTrue(warnings().toString(), warnings().stream().anyMatch(m -> m.contains("email")));

        final PsiFile cls = myFixture.findClass("com.example.Home").getContainingFile();
        replace(cls, "public void onSave() {}", "public void onSave() {}\n    public String getEmail() { return null; }");

        assertFalse(warnings().toString(), warnings().stream().anyMatch(m -> m.contains("email")));
    }

    // ---- Strukturänderungen: nur Dateien, die das Plugin auswertet, verwerfen die projektweiten Caches

    public void testNewComponentSpecIsRecognizedImmediately() {
        configure("Home.html", "<html><body><span jwcid=\"@Fresh\"/></body></html>");
        assertTrue(warnings().toString(), warnings().stream().anyMatch(m -> m.contains("Unknown component type 'Fresh'")));

        myFixture.addFileToProject("Fresh.jwc", component("<component-specification/>"));

        assertFalse(warnings().toString(), warnings().stream().anyMatch(m -> m.contains("Unknown component type 'Fresh'")));
    }

    public void testNewMessageCatalogIsRecognizedImmediately() {
        configure("Home.html", "<html><body><span jwcid=\"@Insert\" value=\"message:fresh\"/></body></html>");
        assertTrue(warnings().toString(), warnings().stream().anyMatch(m -> m.contains("'fresh' not found")));

        myFixture.addFileToProject("Home_de.properties", "fresh=Frisch\n");

        assertFalse(warnings().toString(), warnings().stream().anyMatch(m -> m.contains("'fresh' not found")));
    }

    public void testUnrelatedFilesKeepTheCaches() {
        final TapestryModificationTracker tracker = TapestryModificationTracker.getInstance(getProject());
        // ausstehende Ereignisse aus setUp und vorherigen Tests zuerst abarbeiten
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
        final long before = tracker.getModificationCount();
        myFixture.addFileToProject("notes.txt", "x");
        myFixture.addFileToProject("Compiled.class", "x");
        assertEquals(before, tracker.getModificationCount());

        myFixture.addFileToProject("Other.html", "<html/>");
        assertTrue(tracker.getModificationCount() > before);
    }

    private void replace(PsiFile file, String search, String replacement) {
        final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(file);
        assertNotNull(document);
        final int offset = document.getText().indexOf(search);
        assertTrue(search, offset >= 0);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> {
            document.replaceString(offset, offset + search.length(), replacement);
            PsiDocumentManager.getInstance(getProject()).commitDocument(document);
        });
    }
}
