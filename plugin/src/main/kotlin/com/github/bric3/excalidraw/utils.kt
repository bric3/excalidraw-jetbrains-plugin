package com.github.bric3.excalidraw

import com.github.bric3.excalidraw.editor.ExcalidrawEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager

/**
 * Return a matching editor.
 *
 * Note this may return null if the project or editor the editor is not loaded and selected.
 *
 * @return An ExcalidrawEditor instance or null if the editor is not yet yet ready
 */
fun AnActionEvent.findEditor(): ExcalidrawEditor? {
    val project = this.project ?: return null
    return FileEditorManager.getInstance(project).selectedEditor as? ExcalidrawEditor ?: return null
}


fun ByteArray.toHex(): String {
    return joinToString(" ") { "%02x".format(it) }
}
