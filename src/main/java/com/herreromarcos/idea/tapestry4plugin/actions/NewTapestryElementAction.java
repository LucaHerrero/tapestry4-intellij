package com.herreromarcos.idea.tapestry4plugin.actions;

import com.herreromarcos.idea.tapestry4plugin.TapestryConstants;
import com.herreromarcos.idea.tapestry4plugin.TapestryIcons;
import com.intellij.ide.IdeView;
import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiNameHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.Icon;

/** Legt Spezifikation (.page/.jwc) und HTML-Template für eine neue Seite bzw. Komponente an. */
public abstract class NewTapestryElementAction extends AnAction {
    private final boolean page;

    protected NewTapestryElementAction(final boolean page, final Icon icon) {
        super(page ? "Tapestry 4 Page" : "Tapestry 4 Component",
                page ? "Create a Tapestry 4 page (.page + .html)" : "Create a Tapestry 4 component (.jwc + .html)", icon);
        this.page = page;
    }

    public static class Page extends NewTapestryElementAction {
        public Page() {
            super(true, TapestryIcons.PAGE);
        }
    }

    public static class Component extends NewTapestryElementAction {
        public Component() {
            super(false, TapestryIcons.COMPONENT);
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
        final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
        e.getPresentation().setEnabledAndVisible(e.getProject() != null && view != null && view.getDirectories().length > 0);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
        final Project project = e.getProject();
        final IdeView view = e.getData(LangDataKeys.IDE_VIEW);
        if (project == null || view == null) return;
        final PsiDirectory directory = view.getOrChooseDirectory();
        if (directory == null) return;

        final String kind = page ? "page" : "component";
        final String title = "New Tapestry 4 %s".formatted(StringUtil.capitalize(kind));
        final String input = Messages.showInputDialog(project, "Name of the new %s:".formatted(kind), title,
                page ? TapestryIcons.PAGE : TapestryIcons.COMPONENT);
        if (StringUtil.isEmptyOrSpaces(input)) return;
        final String name = input.trim();
        if (!PsiNameHelper.getInstance(project).isIdentifier(name)) {
            Messages.showErrorDialog(project, "'%s' is not a valid name".formatted(name), title);
            return;
        }
        final String className = StringUtil.trim(Messages.showInputDialog(project, "Fully qualified class (optional):", "Tapestry %s Class".formatted(kind), null));
        if (!StringUtil.isEmpty(className) && !PsiNameHelper.getInstance(project).isQualifiedName(className)) {
            Messages.showErrorDialog(project, "'%s' is not a valid class name".formatted(className), title);
            return;
        }

        final String specName = "%s.%s".formatted(name, page ? TapestryConstants.EXT_PAGE : TapestryConstants.EXT_COMPONENT);
        final String templateName = "%s.%s".formatted(name, TapestryConstants.TEMPLATE_EXT);
        if (directory.findFile(specName) != null || directory.findFile(templateName) != null) {
            Messages.showErrorDialog(project, "%s or %s already exists".formatted(specName, templateName), title);
            return;
        }
        final String specText = specification(className);
        final String templateText = template(name);

        final PsiFile created = WriteCommandAction.writeCommandAction(project).withName("Create Tapestry %s".formatted(kind)).compute(() -> {
            final PsiFileFactory factory = PsiFileFactory.getInstance(project);
            final PsiFile spec = (PsiFile) directory.add(factory.createFileFromText(specName, XmlFileType.INSTANCE, specText));
            directory.add(factory.createFileFromText(templateName, HtmlFileType.INSTANCE, templateText));
            return spec;
        });
        final PsiFile template = directory.findFile(templateName);
        if (template != null && template.getVirtualFile() != null) {
            FileEditorManager.getInstance(project).openFile(template.getVirtualFile(), false);
        }
        if (created.getVirtualFile() != null) FileEditorManager.getInstance(project).openFile(created.getVirtualFile(), true);
    }

    private String specification(final String className) {
        final String root = page ? TapestryConstants.ROOT_PAGE : TapestryConstants.ROOT_COMPONENT;
        final String classAttr = StringUtil.isEmptyOrSpaces(className) ? "" : " class=\"%s\"".formatted(className.trim());
        final String extra = page ? "" : " allow-body=\"yes\" allow-informal-parameters=\"yes\"";
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE %1$s PUBLIC
                        "-//Apache Software Foundation//Tapestry Specification 4.0//EN"
                        "http://jakarta.apache.org/tapestry/dtd/Tapestry_4_0.dtd">
                <%1$s%2$s%3$s>
                    <description></description>
                </%1$s>
                """.formatted(root, classAttr, extra);
    }

    private String template(String name) {
        if (page) {
            return """
                    <html jwcid="@Shell" title="%1$s">
                    <body jwcid="@Body">

                    </body>
                    </html>
                    """.formatted(name);
        }
        return """
                <div jwcid="$content$">

                    <span jwcid="@RenderBody"/>
                </div>
                """;
    }
}
