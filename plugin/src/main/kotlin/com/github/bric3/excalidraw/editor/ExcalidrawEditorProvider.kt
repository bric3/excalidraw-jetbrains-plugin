package com.github.bric3.excalidraw.editor

import com.github.bric3.excalidraw.files.ExcalidrawUtil
import com.intellij.openapi.fileEditor.AsyncFileEditorProvider
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ExcalidrawEditorProvider : AsyncFileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = ExcalidrawUtil.isExcalidrawFile(file)
    override fun createEditor(project: Project, file: VirtualFile): FileEditor = createEditorAsync(project, file).build()
    override fun getEditorTypeId() = "excalidraw-jcef-editor"
    override fun getPolicy() = FileEditorPolicy.HIDE_DEFAULT_EDITOR
    override fun createEditorAsync(project: Project, file: VirtualFile): AsyncFileEditorProvider.Builder =
        object : AsyncFileEditorProvider.Builder() {
            override fun build(): FileEditor = ExcalidrawEditor(project, file)
        }
}