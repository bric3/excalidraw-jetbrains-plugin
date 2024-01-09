package com.github.bric3.excalidraw.files

import com.intellij.openapi.util.Key

enum class ExcalidrawImageType(val extension: String) {
    SVG("svg") {
        override val base64Header = ""
        override val mimeType = "image/svg+xml"
    },
    PNG("png") {
        override val base64Header = "data:image/png;base64,"
        override val mimeType = "image/png"
    },
    JPG("jpg") {
        override val base64Header = "data:image/jpeg;base64,"
        override val mimeType = "image/jpeg"
    },
    WEBP("webp") {
        override val base64Header = "data:image/webp;base64,"
        override val mimeType = "image/webp"
    },
    EXCALIDRAW("excalidraw") {
        override val base64Header = ""
        override val mimeType = "application/json"

    };

    abstract val mimeType: String

    /**
     * The base 64 header returned by excalidraw when exporting to binary format.
     */
    abstract val base64Header: String
}

val EXCALIDRAW_IMAGE_TYPE = Key<ExcalidrawImageType>("ExcalidrawImageType")
