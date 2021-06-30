package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.files.ExcalidrawImageType
import java.util.*


class ExportToPngAction : ExportAction(ExcalidrawImageType.PNG) {
    override fun convertToByteArray(payload: String) : ByteArray {
        val decoded = Base64.getDecoder().decode(payload.substringAfter("data:image/png;base64,"))


        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        if (decoded.size > 7) {
            decoded.sliceArray(0..7).also {
                println("PNG file (header: ${it.toHex()}) ? : ${pngHeader.contentEquals(pngHeader)}")
            }
        }


        return decoded
    }

    fun ByteArray.toHex(): String {
        return joinToString("") { "%02x".format(it) }
    }
}