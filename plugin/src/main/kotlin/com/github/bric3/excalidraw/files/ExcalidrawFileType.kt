package com.github.bric3.excalidraw.files

import com.github.bric3.excalidraw.ExcalidrawJson
import com.intellij.openapi.fileTypes.LanguageFileType
import icons.ExcalidrawIcons

/**
 * Identify Excalidraw file types
 */
object ExcalidrawFileType : LanguageFileType(ExcalidrawJson, true)
//                            , OSFileIdeAssociation // uncomment when icons are ready
{
    override fun getName() = "Excalidraw sketch"
    override fun getDescription() = "Excalidraw sketch file"
    override fun getDefaultExtension() = "excalidraw"
    override fun getIcon() = ExcalidrawIcons.ExcalidrawFileIcon
}