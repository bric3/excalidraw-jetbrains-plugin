package com.github.bric3.excalidraw.files

import com.intellij.ide.highlighter.XmlLikeFileType
import com.intellij.lang.xml.XMLLanguage

/**
 * Identify Excalidraw file types
 */
object ExcalidrawSvgFileType : XmlLikeFileType(XMLLanguage.INSTANCE)
//                            , OSFileIdeAssociation // uncomment when icons are ready
{
    override fun getName() = "Excalidraw SVG Export"
    override fun getDescription() = "Excalidraw sketch exported to SVG"
    override fun getDefaultExtension() = "excalidraw.svg"
    override fun getIcon() = ExcalidrawIcon.ICON
}