package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SceneModes
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleZenModeAction : ToggleSceneModeAction() {
    override fun toggle(sceneModes: SceneModes, state: Boolean) {
        sceneModes.zenMode = state
    }

    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSceneModes().zenMode ?: false
}