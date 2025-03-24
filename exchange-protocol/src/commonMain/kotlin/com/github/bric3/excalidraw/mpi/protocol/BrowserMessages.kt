package com.github.bric3.excalidraw.mpi.protocol

import com.github.bric3.excalidraw.mpi.protocol.model.SearchResult
import kotlinx.serialization.Serializable

object BrowserMessages {
  @Serializable
  class AskForwardSearchData

  @Serializable
  data class SearchResponse(val result: SearchResult)
}
