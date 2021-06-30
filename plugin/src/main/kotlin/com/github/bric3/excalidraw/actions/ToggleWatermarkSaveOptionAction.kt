package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleWatermarkSaveOptionAction : ToggleSaveOptionAction() {
    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSaveOptions().shouldAddWatermark ?: false

    override fun toggle(saveOptions: SaveOptions, state: Boolean) {
        saveOptions.shouldAddWatermark = state
//        saveOptions.shouldAddWatermark = when (saveOptions.shouldAddWatermark) {
//            null, false -> true
//            true -> false
//        }
    }
}