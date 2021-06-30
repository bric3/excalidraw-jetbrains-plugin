package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleBackgroundSaveOptionAction : ToggleSaveOptionAction() {
    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSaveOptions().exportBackground ?: false

    override fun toggle(saveOptions: SaveOptions, state: Boolean) {
        saveOptions.exportBackground = state
    }
}