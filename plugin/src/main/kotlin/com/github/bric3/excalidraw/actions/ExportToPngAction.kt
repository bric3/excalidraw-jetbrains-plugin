package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.toHex
import java.util.*


class ExportToPngAction : ExportAction(ExcalidrawImageType.PNG) {
    override fun convertToByteArray(payload: String): ByteArray {
        return Base64.getDecoder().decode(payload.substringAfter("data:image/png;base64,")).also {
            assertPngHeader(it)
        }
    }

    private fun assertPngHeader(decoded: ByteArray) {
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        if (decoded.size <= 7) throw IllegalArgumentException("Not a PNG file, got '${decoded.toHex()}'")
        decoded.sliceArray(0..7).also {
            if(!pngHeader.contentEquals(pngHeader)) {
                throw IllegalArgumentException("Not a PNG file, got '${it.toHex()}'")
            }
        }
    }
}