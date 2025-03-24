package com.github.bric3.excalidraw.mpi

fun interface MessageReceivedHandler {
  /**
   * Called then [MessagePipe] receives message from its other end.
   *
   * @param data raw data received from the other end of [MessagePipe]
   */
  fun messageReceived(data: String)
}
