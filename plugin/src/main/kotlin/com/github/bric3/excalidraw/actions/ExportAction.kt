package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.debuggingLogWithThread
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.findEditor
import com.github.bric3.excalidraw.writePayloadToFile
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileSaverDialog
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

abstract class ExportAction(val type: ExcalidrawImageType) : AnAction() {
    private val logger = thisLogger()

    override fun update(event: AnActionEvent) {
        // Using the event, evaluate the context, and enable or disable the action.
    }

    override fun actionPerformed(event: AnActionEvent) {
        val excalidrawEditor = event.findEditor() ?: return
        val saveOptions = excalidrawEditor.getUserData(SaveOptions.SAVE_OPTIONS_KEY) ?: SaveOptions()

        val descriptor = FileSaverDescriptor(
            "Export Image To",
            "Choose the image destination",
            type.extension
        )
        val saveFileDialog: FileSaverDialog =
            FileChooserFactory.getInstance().createSaveFileDialog(descriptor, null as Project?)

        val destination = saveFileDialog.save(
            excalidrawEditor.file.parent,
            "${excalidrawEditor.file.nameWithoutExtension}.${type.extension}"
        )
        if (destination != null) {
            debuggingLogWithThread(logger) { "Export ${type.name} to destination : ${destination.file}" }
            if (destination.file.extension != type.extension) {
                Notifications.Bus.notify(
                    Notification(
                        "excalidraw.error",
                        "Image format non supported",
                        "This action only supports ${type.name} file exports",
                        NotificationType.ERROR,
                    )
                )
            }

            CoroutineScope(Dispatchers.IO + CoroutineName(this::class.java.simpleName)).launch {
                val payload = excalidrawEditor.viewController.triggerSnapshot(type, saveOptions)

                writePayloadToFile(
                    { destination.getVirtualFile(true)!! },
                    type,
                    convertToByteArray(payload)
                )
            }
        }
    }


    protected abstract fun convertToByteArray(payload: String): ByteArray

}
