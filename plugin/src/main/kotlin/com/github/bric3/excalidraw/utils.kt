package com.github.bric3.excalidraw

fun ByteArray.toHex(): String {
    return joinToString(" ") { "%02x".format(it) }
}