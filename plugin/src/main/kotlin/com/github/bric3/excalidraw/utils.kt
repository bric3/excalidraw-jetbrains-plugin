package com.github.bric3.excalidraw

import com.github.bric3.excalidraw.editor.ExcalidrawEditor
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.workspaceModel.storage.impl.EntityStorageSerializerImpl.Companion.logger
import org.jetbrains.concurrency.AsyncPromise
import java.io.IOException

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

fun asyncWrite(
    destination: () -> VirtualFile,
    type: ExcalidrawImageType,
    byteArrayPayload: ByteArray
): AsyncPromise<Boolean> {
    val writeDone = AsyncPromise<Boolean>()
    ApplicationManager.getApplication().invokeLater {
        ApplicationManager.getApplication().runWriteAction {
            logWithThread("utils::asyncWrite")
            val file = destination.invoke()
            try {
                file.getOutputStream(file).use { stream ->
                    with(stream) {
                        write(byteArrayPayload)
                    }
                }
                writeDone.setResult(true)
            } catch (e: IOException) {
                writeDone.setError(e)
                notifyAboutWriteError(type, file, e)
            } catch (e: IllegalArgumentException) {
                writeDone.setError(e)
                notifyAboutWriteError(type, file, e)
            }
        }
    }
    return writeDone
}

fun logWithThread(message: String) {
    if (debugMode) {
        println(Thread.currentThread().name + "(" + Thread.currentThread().id + "): " + message)
    }
}


val debugMode = ProcessHandle.current().info().arguments().map {
    it.any { it.contains("-agentlib:jdwp") }
}.orElse(false)!!
