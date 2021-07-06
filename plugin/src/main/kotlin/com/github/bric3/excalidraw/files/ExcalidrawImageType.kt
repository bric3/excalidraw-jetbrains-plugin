package com.github.bric3.excalidraw.files

import com.intellij.openapi.util.Key

enum class ExcalidrawImageType(val extension: String) {
    SVG("svg"),
    PNG("png"),
    EXCALIDRAW("excalidraw");
}

val EXCALIDRAW_IMAGE_TYPE = Key<ExcalidrawImageType>("ExcalidrawImageType")
