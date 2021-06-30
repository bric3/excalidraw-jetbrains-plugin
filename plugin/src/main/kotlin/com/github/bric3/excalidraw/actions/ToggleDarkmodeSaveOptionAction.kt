package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleDarkmodeSaveOptionAction : ToggleSaveOptionAction() {
    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSaveOptions().exportWithDarkMode ?: false

    override fun toggle(saveOptions: SaveOptions, state: Boolean) {
        saveOptions.exportWithDarkMode = state
//        saveOptions.exportWithDarkMode = when (saveOptions.exportWithDarkMode) {
//            null, false -> true
//            true -> false
//        }
    }
}