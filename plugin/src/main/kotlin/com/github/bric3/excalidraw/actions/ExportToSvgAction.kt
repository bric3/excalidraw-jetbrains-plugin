package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.editor.ExcalidrawEditor
import com.github.bric3.excalidraw.files.ExcalidrawImageType
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
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.IOException


class ExportToSvgAction : AnAction() {
    private val logger = thisLogger()

    override fun update(event: AnActionEvent) {
        // Using the event, evaluate the context, and enable or disable the action.
    }

    override fun actionPerformed(event: AnActionEvent) {
        val excalidrawEditor = findEditor(event) ?: return
        // toto dialog


        val descriptor = FileSaverDescriptor(
            "Export Image to", "Choose the destination file",
            "svg"
        )
        val saveFileDialog: FileSaverDialog =
            FileChooserFactory.getInstance().createSaveFileDialog(descriptor, null as Project?)

        val destination = saveFileDialog.save(excalidrawEditor.file.parent,
                                              excalidrawEditor.file.nameWithoutExtension + ".svg")
        if (destination != null) {
            logger.debug("Export SVG destination : ${destination.file}")
            if (destination.file.extension != "svg") {
                Notifications.Bus.notify(
                    Notification(
                        "excalidraw.error",
                        "Image format non supported",
                        "This action only supports SVG file exports",
                        NotificationType.ERROR,
                        null
                    )
                )
            }

            excalidrawEditor.viewController.saveAs(ExcalidrawImageType.SVG).then { payload ->
                ApplicationManager.getApplication().invokeLater {
                    ApplicationManager.getApplication().runWriteAction {
                        try {
                            VfsUtil.saveText(destination.getVirtualFile(true)!!, payload!!)
                        } catch (e: IOException) {
                            logger.error("Could not write to ${destination.virtualFile}", e)
                            Notifications.Bus.notify(
                                Notification(
                                    "excalidraw.error",
                                    "Writing export to disk failed",
                                    "This action failed to write the SVG content to disk.",
                                    NotificationType.ERROR,
                                    null
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun findEditor(event: AnActionEvent): ExcalidrawEditor? {
        val project = event.project ?: return null
        return FileEditorManager.getInstance(project).selectedEditor as? ExcalidrawEditor ?: return null
    }
}