package com.herreromarcos.idea.tapestry4plugin;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.List;

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

    private List<String> errors() {
        return highlightingMessages(HighlightSeverity.ERROR);
    }

    private List<String> warnings() {
        return highlightingMessages(HighlightSeverity.WARNING);
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
