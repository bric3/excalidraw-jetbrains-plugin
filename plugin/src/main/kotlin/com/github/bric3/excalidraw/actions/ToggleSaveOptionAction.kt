package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.findEditor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

abstract class ToggleSaveOptionAction : ToggleAction() {
    override fun setSelected(event: AnActionEvent, state: Boolean) {
        val saveOptions = event.getSaveOptions()

        toggle(saveOptions, state)
    }

    protected abstract fun toggle(saveOptions: SaveOptions, state: Boolean)

    protected fun AnActionEvent.getSaveOptions() : SaveOptions {
        val excalidrawEditor = this.findEditor() ?: throw IllegalStateException("No excalidraw editor found")
        var saveOptions = excalidrawEditor.getUserData(SaveOptions.SAVE_OPTIONS_KEY)
        if (saveOptions == null) {
            saveOptions = SaveOptions()
            excalidrawEditor.putUserData(SaveOptions.SAVE_OPTIONS_KEY, saveOptions)
        }
        return saveOptions
    }
}