package com.github.bric3.excalidraw.mpi

import kotlinx.serialization.Serializable

/**
 * This is an internal message wrapper used by message passing protocol.
 */
@Serializable
data class PackedMessage(
  val type: String,
  val data: String
)
