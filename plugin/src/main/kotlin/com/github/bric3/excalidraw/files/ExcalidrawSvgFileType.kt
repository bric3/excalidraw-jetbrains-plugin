package com.github.bric3.excalidraw.files

import com.intellij.ide.highlighter.XmlLikeFileType
import com.intellij.lang.xml.XMLLanguage
import icons.ExcalidrawIcons

/**
 * Identify SVG files with an Excalidraw scene
 */
object ExcalidrawSvgFileType : XmlLikeFileType(XMLLanguage.INSTANCE) {
    override fun getName() = "Excalidraw SVG Export"
    override fun getDescription() = "Excalidraw sketch exported to SVG"
    override fun getDefaultExtension() = "excalidraw.svg"
    override fun getIcon() = ExcalidrawIcons.ExcalidrawFileIcon
}
