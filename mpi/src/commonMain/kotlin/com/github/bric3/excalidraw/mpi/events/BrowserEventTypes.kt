package com.github.bric3.excalidraw.mpi.events

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

// TODO Remove
@ExperimentalJsExport
@JsExport
object BrowserEventTypes {
  val PAGE_CHANGED by PropertyNameDelegate
  val DOCUMENT_INFO by PropertyNameDelegate
  val PRESENTATION_MODE_ENTER_READY by PropertyNameDelegate
  val PRESENTATION_MODE_ENTER by PropertyNameDelegate
  val PRESENTATION_MODE_EXIT by PropertyNameDelegate
  val FRAME_FOCUSED by PropertyNameDelegate
  val PAGES_COUNT by PropertyNameDelegate
  val DOCUMENT_LOAD_ERROR by PropertyNameDelegate
  val UNHANDLED_ERROR by PropertyNameDelegate
  val SIDEBAR_VIEW_STATE_CHANGED by PropertyNameDelegate
  val SIDEBAR_AVAILABLE_VIEWS_CHANGED by PropertyNameDelegate
}
