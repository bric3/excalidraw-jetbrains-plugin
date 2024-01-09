package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.intellij.openapi.actionSystem.AnActionEvent
import icons.ExcalidrawIcons

class ToggleLightDarkModeSaveOptionAction : ToggleSaveOptionAction() {
    override fun update(e: AnActionEvent) {
        e.presentation.icon = if (isSelected(e))
            ExcalidrawIcons.saveConfig_lightMode
        else
            ExcalidrawIcons.saveConfig_darkMode

        e.presentation.text = if (isSelected(e))
            "Save Using Light Mode, Switch to Use Dark Mode"
        else
            "Save Using Dark Mode, Switch to Use Light Mode"
    }

    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSaveOptions()?.exportWithDarkMode ?: false

    override fun toggle(saveOptions: SaveOptions, state: Boolean) {
        saveOptions.exportWithDarkMode = state
    }
}
