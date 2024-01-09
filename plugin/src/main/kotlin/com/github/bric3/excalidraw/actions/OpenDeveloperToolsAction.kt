package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.debugMode
import com.github.bric3.excalidraw.findEditor
import com.intellij.ide.actions.CloseAction
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.Key

class OpenDeveloperToolsAction : DumbAwareAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = debugMode
    }

    override fun actionPerformed(e: AnActionEvent) {
        // Fails if called a second time
        // https://youtrack.jetbrains.com/issue/IDEA-285655/Selenium-Get-com.intellij.util.IncorrectOperationException-on-reopen-Developer-Tools-in-PageObjectGenerator
        val editor = e.findEditor() ?: return

        if (HAS_OPEN_DEVTOOLS.get(editor) == true) {
            // Workaround for IDEA-285655 to try recycling editor
            MessageDialogBuilder.okCancel(
                "Cannot reopen devtools, need to re-open this file (due to IDEA-285655)",
                "Do you want to close this editor?"
            ).let {
                if (it.ask(editor.component)) {
                    e.getData(CloseAction.CloseTarget.KEY)?.close()
                }
            }
        } else {
            editor.viewController.openDevTools()
            HAS_OPEN_DEVTOOLS.set(editor, true)
        }
    }

    companion object {
        val HAS_OPEN_DEVTOOLS = Key<Boolean>("excalidraw.HAS_OPEN_DEVTOOLS")
    }
}
