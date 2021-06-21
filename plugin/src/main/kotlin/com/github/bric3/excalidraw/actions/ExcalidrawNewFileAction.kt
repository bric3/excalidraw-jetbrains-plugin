package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.files.ExcalidrawIcon
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.NonEmptyInputValidator
import com.intellij.psi.PsiDirectory

class ExcalidrawNewFileAction : CreateFileFromTemplateAction(
    "Excalidraw File",
    "Create new Excalidraw file",
    ExcalidrawIcon.ICON
),
                                DumbAware {
    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        // templates src/main/resources/fileTemplates.internal
        builder
            .setTitle("New Excalidraw Sketch")
            .addKind(".excalidraw file", ExcalidrawIcon.ICON, "new-sketch")
            .setValidator(NonEmptyInputValidator())
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String {
        return "Excalidraw Sketch File"
    }
}