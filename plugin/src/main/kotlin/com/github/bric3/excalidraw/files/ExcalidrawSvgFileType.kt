package com.github.bric3.excalidraw.files

import com.github.bric3.excalidraw.Excalidraw
import com.intellij.ide.highlighter.XmlLikeFileType

/**
 * Identify Excalidraw file types
 */
object ExcalidrawSvgFileType : XmlLikeFileType(Excalidraw)
//                            , OSFileIdeAssociation // uncomment when icons are ready
{
    override fun getName() = "Excalidraw SVG Export"
    override fun getDescription() = "Excalidraw sketch exported to SVG"
    override fun getDefaultExtension() = "excalidraw.svg"
    override fun getIcon() = ExcalidrawIcon.ICON
}