package com.github.bric3.excalidraw.scratch

import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project

class ExcalidrawScratchFileCreationHelper : ScratchFileCreationHelper() {
    override fun prepareText(project: Project, context: Context, dataContext: DataContext): Boolean {

        val blankExcalidrawTemplate = FileTemplateManager.getInstance(project).getInternalTemplate("new-sketch")

        context.text = blankExcalidrawTemplate.getText(emptyMap<Any, Any>())
        return true
    }
}