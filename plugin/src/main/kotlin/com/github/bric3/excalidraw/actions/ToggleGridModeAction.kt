package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SceneModes
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleGridModeAction : ToggleSceneModeAction() {
    override fun toggle(sceneModes: SceneModes, state: Boolean) {
        sceneModes.gridMode = state
    }

    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSceneModes()?.gridMode ?: false
}
