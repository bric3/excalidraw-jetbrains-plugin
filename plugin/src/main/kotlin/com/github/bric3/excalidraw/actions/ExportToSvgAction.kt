package com.github.bric3.excalidraw.actions

import com.github.bric3.excalidraw.files.ExcalidrawImageType
import java.nio.charset.StandardCharsets


class ExportToSvgAction : ExportAction(ExcalidrawImageType.SVG) {
    override fun convertToByteArray(payload: String) = payload.toByteArray(StandardCharsets.UTF_8)
}