package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SceneModes
import com.github.bric3.excalidraw.findEditor
import com.github.bric3.excalidraw.support.ExcalidrawColorScheme
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.util.ui.UIUtil

class ToggleLightDarkModeAction : ToggleSceneModeAction() {
    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val sceneModes = event.getSceneModes() ?: return

        toggle(sceneModes, state)

        // This SceneMode value gets passed to a different
        // method than other scene mode toggles

        val mode = if (state) ExcalidrawColorScheme.LIGHT else ExcalidrawColorScheme.DARK

        event.findEditor()!!.viewController.changeTheme(mode.key)
    }

    override fun toggle(sceneModes: SceneModes, state: Boolean) {
        sceneModes.lightMode = state
    }

    // Default selected state is derived from the UI theme
    // if user has not set a state
    override fun isSelected(event: AnActionEvent): Boolean =
        event.getSceneModes()?.lightMode ?: !UIUtil.isUnderDarcula()

}