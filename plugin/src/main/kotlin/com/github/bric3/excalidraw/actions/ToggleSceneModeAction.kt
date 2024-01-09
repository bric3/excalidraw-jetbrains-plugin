package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SceneModes
import com.github.bric3.excalidraw.findEditor
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

abstract class ToggleSceneModeAction : ToggleAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val sceneModes = event.getSceneModes() ?: return

        toggle(sceneModes, state)

        event.findEditor()!!.viewController.toggleModes(sceneModes)
    }

    protected abstract fun toggle(sceneModes: SceneModes, state: Boolean)

    protected fun AnActionEvent.getSceneModes() : SceneModes? {
        val excalidrawEditor = this.findEditor() ?: return null
        var sceneModes = excalidrawEditor.getUserData(SceneModes.SCENE_MODES_KEY)
        if (sceneModes == null) {
            sceneModes = SceneModes()
            excalidrawEditor.putUserData(SceneModes.SCENE_MODES_KEY, sceneModes)
        }
        return sceneModes
    }
}
