package com.github.bric3.excalidraw.editor

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.io.IOException
import java.io.InputStream
import java.net.URI

/**
 * Creates a Scheme handler for the CEF application
 *
 * This comes from
 * <a href="https://github.com/docToolchain/diagrams.net-intellij-plugin/blob/14a4c8f7da38e5e4952e7046740493994f726e57/src/main/kotlin/de/docs_as_co/intellij/plugin/drawio/utils/SchemeHandlerFactory.kt">
 *     docToolchain/diagrams.net-intellij-plugin
 * </a> however it contains some modifications.
 */
class SchemeHandlerFactory(val getStream: (uri: URI) -> InputStream?) : CefSchemeHandlerFactory {
    override fun create(browser: CefBrowser, frame: CefFrame, schemeName: String, request: CefRequest): CefResourceHandler {
        val uri = URI(request.url)

        val myStream = getStream(uri)

        return object : CefResourceHandler {
            override fun processRequest(req: CefRequest, callback: CefCallback): Boolean {
                callback.Continue()
                return true
            }

            override fun getResponseHeaders(response: CefResponse, responseLength: IntRef, redirectUrl: StringRef?) {

                when {
                    uri.path.endsWith(".html") -> response.mimeType = "text/html"
                    uri.path.endsWith(".js") -> response.mimeType = "application/javascript"
                    uri.path.endsWith(".css") -> response.mimeType = "text/css"
                    uri.path.endsWith(".svg") -> response.mimeType = "image/svg+xml"
                    uri.path.endsWith(".png") -> response.mimeType = "image/png"
                }

                if (myStream === null) {
                    response.status = 404
                } else {
                    response.status = 200
                }
            }

            override fun readResponse(dataOut: ByteArray, bytesToRead: Int, bytesRead: IntRef, callback: CefCallback): Boolean {
                if (myStream === null) {
                    bytesRead.set(0)
                    return false
                }
                try {
                    val availableSize = myStream.available()
                    return if (availableSize > 0) {
                        bytesRead.set(myStream.read(dataOut, 0, bytesToRead.coerceAtMost(availableSize)))
                        true
                    } else {
                        bytesRead.set(0)
                        try {
                            myStream.close()
                        } catch (_: IOException) {
                        }

                        false
                    }
                } catch (ex: IOException) {

                    return false
                }
            }

            override fun cancel() {
            }
        }
    }
}
