package com.github.bric3.excalidraw.mpi.protocol

import com.github.bric3.excalidraw.mpi.protocol.model.SearchDirection
import com.github.bric3.excalidraw.mpi.protocol.model.SearchQuery
import kotlinx.serialization.Serializable

object IdeMessages {
    @Serializable
    data class Search(val query: SearchQuery, val direction: SearchDirection)

    @Serializable
    class ReleaseSearchHighlighting
}
