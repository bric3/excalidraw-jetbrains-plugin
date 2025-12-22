package com.github.bric3.excalidraw

import com.github.bric3.excalidraw.editor.ExcalidrawEditor
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.files.ExcalidrawImageType.SVG
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import java.io.IOException
import java.util.function.Supplier

private val logger = Logger.getInstance("com.github.bric3.excalidraw.utils")

val debugMode = ProcessHandle.current().info().arguments().map {
    it.any { arg -> arg.contains("-agentlib:jdwp") }
}.orElse(false)!!


/**
 * Return a matching editor.
 *
 * Note this may return null if the project or the editor is not loaded and selected.
 *
 * @return An `ExcalidrawEditor` instance or null if the editor is not yet ready
 */
fun AnActionEvent.findEditor(): ExcalidrawEditor? {
    val project = this.project ?: return null
    val psiFile = this.dataContext.getData(CommonDataKeys.PSI_FILE) ?: return null
    val editor = FileEditorManager.getInstance(project).selectedEditors.find {
        psiFile.virtualFile.equals(it.file)
    }

    return editor as? ExcalidrawEditor ?: return null
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
        )
    )
}

suspend fun writePayloadToFile(
    destination: () -> VirtualFile,
    type: ExcalidrawImageType,
    byteArrayPayload: ByteArray
) {
    readAndWriteAction {
        writeAction {
            debuggingLogWithThread(logger) { "utils::writePayloadToFile" }
            val file = destination.invoke()
            try {
                file.getOutputStream(file).use { stream ->
                    with(stream) {
                        write(byteArrayPayload)
                    }
                }
            } catch (e: IOException) {
                notifyAboutWriteError(type, file, e)
            } catch (e: IllegalArgumentException) {
                notifyAboutWriteError(type, file, e)
            }
        }
    }
}

suspend fun writePayloadToDocument(
    destination: () -> VirtualFile,
    strPayload: CharSequence
) {
    val file = destination()
    val doc = readAction(file::findDocument)
    readAndWriteAction {
        writeAction {
            debuggingLogWithThread(logger) { "utils::writePayloadToDocument" }
            doc?.let { document ->
                document.setText(strPayload)
                return@writeAction
            } ?: logger.debug("Could not find document for $file")
        }
    }
}

/**
 * Patch for svg files where fonts assets use the plugin root
 * instead of regular `excalidraw.com`.
 *
 * https://github.com/excalidraw/excalidraw/issues/7543
 *
 * Updated for Excalidraw 0.18.0 where font paths changed from
 * `dist/excalidraw-assets/` to `fonts/`
 */
val wrongAssetRoot = "https://excalidraw-jetbrains-plugin//?(dist/excalidraw-assets/|fonts/)".toRegex()
fun patchSvgForExcalidraw7543(payload: String, type: ExcalidrawImageType = SVG) =
    when (type) {
        SVG -> payload.replace(wrongAssetRoot, "https://excalidraw.com/")
        else -> payload
    }

fun debuggingLogWithThread(logger: Logger, message: Supplier<String>) {
    if (debugMode) {
        logger.info("[${Thread.currentThread().name} (${Thread.currentThread().id})] ${message.get()}")
    }
}
