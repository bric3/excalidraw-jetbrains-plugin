package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.findEditor
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFileWrapper
import org.jetbrains.annotations.Nullable
import java.io.IOException

abstract class ExportAction(val type: ExcalidrawImageType) : AnAction() {
    private val logger = thisLogger()

    override fun update(event: AnActionEvent) {
        // Using the event, evaluate the context, and enable or disable the action.
    }

    override fun actionPerformed(event: AnActionEvent) {
        val excalidrawEditor = event.findEditor() ?: return
        val saveOptions = excalidrawEditor.getUserData(SaveOptions.SAVE_OPTIONS_KEY) ?: SaveOptions()


        val descriptor = FileSaverDescriptor(
            "Export Image to",
            "Choose the image destination",
            type.extension
        )
        val saveFileDialog: FileSaverDialog =
            FileChooserFactory.getInstance().createSaveFileDialog(descriptor, null as Project?)

        val destination = saveFileDialog.save(excalidrawEditor.file.parent,
                                              "${excalidrawEditor.file.nameWithoutExtension}.${type.extension}"
        )
        if (destination != null) {
            logger.debug("Export ${type.name} to destination : ${destination.file}")
            if (destination.file.extension != type.extension) {
                Notifications.Bus.notify(
                    Notification(
                        "excalidraw.error",
                        "Image format non supported",
                        "This action only supports ${type.name} file exports",
                        NotificationType.ERROR,
                        null
                    )
                )
            }

            excalidrawEditor.viewController.saveAs(type, saveOptions).then { payload ->
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        try {
                            val file = destination.getVirtualFile(true)!!

                            file.getOutputStream(file).use { stream -> with(stream) {
                                write(convertToByteArray(payload))
                            } }
                        } catch (e: IOException) {
                            notifyAboutWriteError(destination, e)
                        } catch (e: IllegalArgumentException) {
                            notifyAboutWriteError(destination, e)
                        }
                    }
                }
            }
        }
    }

    protected abstract fun convertToByteArray(payload: String): ByteArray

    private fun notifyAboutWriteError(
        destination: @Nullable VirtualFileWrapper,
        ex: Exception
    ) {
        logger.error("Could not write to ${destination.virtualFile}", ex)
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
}