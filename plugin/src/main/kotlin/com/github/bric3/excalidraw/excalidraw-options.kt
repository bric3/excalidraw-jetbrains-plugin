package com.github.bric3.excalidraw

import com.intellij.openapi.util.Key

data class SaveOptions(
    var exportBackground : Boolean? = null,
    var shouldAddWatermark: Boolean? = null,
    var exportWithDarkMode: Boolean? = null
) {
    companion object {
        val SAVE_OPTIONS_KEY: Key<SaveOptions> = Key.create(SaveOptions::javaClass.name)
    }
}

data class SceneModes(
    var gridMode: Boolean? = null,
    var zenMode: Boolean? = null,
    var readOnlyMode: Boolean? = null,
    var lightMode: Boolean? = null, // allow overriding uiTheme for the editor window
) {
    companion object {
        val SCENE_MODES_KEY: Key<SceneModes> = Key.create(SceneModes::javaClass.name)
    }
}