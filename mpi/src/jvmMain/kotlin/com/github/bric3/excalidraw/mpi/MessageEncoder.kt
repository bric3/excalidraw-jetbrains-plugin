package com.github.bric3.excalidraw.mpi

import com.github.bric3.excalidraw.mpi.EncodingUtil.encodeURIComponent
import java.net.URLDecoder

actual object MessageEncoder {
  actual fun encode(data: String): String = encodeURIComponent(data)

  actual fun decode(data: String): String = EncodingUtil.decodeURIComponent(data)
}


