package com.github.bric3.excalidraw.files

import com.github.bric3.excalidraw.ExcalidrawJson
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.OSFileIdeAssociation
import icons.ExcalidrawIcons

/**
 * Identify Excalidraw file types
 */
object ExcalidrawFileType : LanguageFileType(ExcalidrawJson, true), OSFileIdeAssociation {
    override fun getName() = "Excalidraw sketch"
    override fun getDescription() = "Excalidraw sketch file"
    override fun getDefaultExtension() = "excalidraw"
    override fun getIcon() = ExcalidrawIcons.ExcalidrawFileIcon
    override fun getDisplayName() = "Excalidraw sketch"
    override fun isSecondary() = false
}
