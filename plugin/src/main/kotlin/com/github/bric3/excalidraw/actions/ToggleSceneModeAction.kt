package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SceneModes
import com.github.bric3.excalidraw.findEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

abstract class ToggleSceneModeAction : ToggleAction() {
    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val sceneModes = event.getSceneModes()

        toggle(sceneModes, state)

        event.findEditor()!!.viewController.toggleModes(sceneModes)
    }

    protected abstract fun toggle(sceneModes: SceneModes, state: Boolean)

    protected fun AnActionEvent.getSceneModes() : SceneModes {
        val excalidrawEditor = this.findEditor() ?: throw IllegalStateException("No excalidraw editor found")
        var sceneModes = excalidrawEditor.getUserData(SceneModes.SCENE_MODES_KEY)
        if (sceneModes == null) {
            sceneModes = SceneModes()
            excalidrawEditor.putUserData(SceneModes.SCENE_MODES_KEY, sceneModes)
        }
        return sceneModes
    }
}