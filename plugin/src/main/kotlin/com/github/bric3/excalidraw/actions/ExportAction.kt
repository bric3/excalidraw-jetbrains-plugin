package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.asyncWrite
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.findEditor
import com.github.bric3.excalidraw.debuggingLogWithThread
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

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
                        null
                    )
                )
            }

            runBlocking {
                GlobalScope.launch {
                    withContext(Dispatchers.Default) {
                        val payload = excalidrawEditor.viewController.saveAsCoroutines(type, saveOptions)
                        asyncWrite(
                            { destination.getVirtualFile(true)!! },
                            type,
                            convertToByteArray(payload)
                        )
                    }
                }
            }
        }
    }


    protected abstract fun convertToByteArray(payload: String): ByteArray

}
