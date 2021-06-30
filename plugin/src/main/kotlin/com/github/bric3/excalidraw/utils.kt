package com.github.bric3.excalidraw

import com.github.bric3.excalidraw.editor.ExcalidrawEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager


fun AnActionEvent.findEditor(): ExcalidrawEditor? {
    val project = this.project ?: return null
    return FileEditorManager.getInstance(project).selectedEditor as? ExcalidrawEditor ?: return null
}


fun ByteArray.toHex(): String {
    return joinToString(" ") { "%02x".format(it) }
}