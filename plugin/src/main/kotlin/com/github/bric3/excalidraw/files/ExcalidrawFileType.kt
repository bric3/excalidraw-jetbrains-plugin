package com.github.bric3.excalidraw.files

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.LanguageFileType

/**
 * Identify Excalidraw file types
 */
object ExcalidrawFileType : LanguageFileType(JsonLanguage.INSTANCE, true)
//                            , OSFileIdeAssociation // uncomment when icons are ready
{
    override fun getName() = "Excalidraw sketch"
    override fun getDescription() = "Excalidraw sketch file"
    override fun getDefaultExtension() = "excalidraw"
    override fun getIcon() = ExcalidrawIcon.ICON
}