package com.github.bric3.excalidraw

import com.github.bric3.excalidraw.editor.ExcalidrawEditor
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl.Companion.logger

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


fun notifyAboutWriteError(
    type: ExcalidrawImageType,
    virtualFile: VirtualFile?,
    ex: Exception,
) {
    logger.error("Could not write to $virtualFile", ex)
    Notifications.Bus.notify(
        Notification(
            "excalidraw.error",
            "Writing export to disk failed",
            "This action failed to write the ${type.name} content to disk.",
            NotificationType.ERROR,
            null
        )
    )
}
