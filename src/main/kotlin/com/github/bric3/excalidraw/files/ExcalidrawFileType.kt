package com.github.bric3.excalidraw.files

import com.github.bric3.excalidraw.Excalidraw
import com.intellij.openapi.fileTypes.LanguageFileType

/**
 * Identify Excalidraw file types
 */
object ExcalidrawFileType : LanguageFileType(Excalidraw) {
    override fun getName() = "Excalidraw sketch"
    override fun getDescription() = "Excalidraw sketch file"
    override fun getDefaultExtension() = "excalidraw"
    override fun getIcon() = ExcalidrawIcon.ICON
}