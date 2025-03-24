package com.github.bric3.excalidraw.mpi

external fun encodeURIComponent(data: String): String

external fun decodeURIComponent(data: String): String

actual object MessageEncoder {
  actual fun encode(data: String): String {
    return encodeURIComponent(data)
  }

  actual fun decode(data: String): String {
    return decodeURIComponent(data)
  }
}
