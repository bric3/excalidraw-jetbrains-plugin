package com.github.bric3.excalidraw.files

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import icons.ExcalidrawIcons

/**
 * Identify SVG files with an Excalidraw scene
 */
object ExcalidrawPngFileType : FileType {
    override fun getName() = "Excalidraw SVG Export"
    override fun getDescription() = "Excalidraw sketch exported to SVG"
    override fun getDefaultExtension() = "excalidraw.svg"
    override fun getIcon() = ExcalidrawIcons.ExcalidrawFileIcon
    override fun isBinary() = true
    override fun getCharset(file: VirtualFile, content: ByteArray?): String? = null
}
