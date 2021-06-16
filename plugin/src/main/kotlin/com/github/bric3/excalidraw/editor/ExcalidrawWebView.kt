package com.github.bric3.excalidraw.editor

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.net.URI

class ExcalidrawWebView(val lifetime: Lifetime, var uiTheme: String) {
    companion object {
        var didRegisterSchemeHandler = false
        fun initializeSchemeHandler(uiTheme: String) {
            didRegisterSchemeHandler = true

            // clear old scheme handler factories in case this is a re-initialization with an updated theme
            CefApp.getInstance().clearSchemeHandlerFactories()

            // initialization ideas from docToolchain/diagrams.net-intellij-plugin
            CefApp.getInstance().registerSchemeHandlerFactory(
                // needed to use "https" as scheme here as "drawio-plugin" scheme didn't allow for CORS requests that were needed
                // to start the diagrams.net application in the JCEF/Chromium preview browser.
                // Worked in previous versions, but not from IntelliJ 2021.1 onwards; maybe due to tightened security in Chromium.
                // Error message was: "CORS policy: Cross origin requests are only supported for protocol schemes..."
                "https", "excalidraw-plugin",
                SchemeHandlerFactory { uri: URI ->
                    if (uri.path == "/index.html") {
                        val text = BufferedReader(ExcalidrawWebView::class.java.getResourceAsStream("/assets/index.html").reader()).readText()
                        text.byteInputStream()
                    } else {
                        BufferedInputStream(ExcalidrawWebView::class.java.getResourceAsStream("/assets" + uri.path))
                    }
                }
            ).also { successful -> assert(successful) }
        }
    }

    private val panel = LoadableJCEFHtmlPanel("https://excalidraw-plugin/index.html", null, null)
    val component = panel.component

    init {
        initializeSchemeHandler(uiTheme)
        object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
//                frame?.executeJavaScript(
//                    "window.sendMessageToHost = function(message) {" +
//                            jsRequestHandler.inject("message") +
//                            "};",
//                    frame.url, 0
//                )
            }
        }.also { handlerAdapter ->
            panel.browser.jbCefClient.addLoadHandler(handlerAdapter, panel.browser.cefBrowser)
            lifetime.onTermination {
                panel.browser.jbCefClient.removeLoadHandler(handlerAdapter, panel.browser.cefBrowser)
            }
        }
    }

    fun reload(uiTheme: String, onThemeChanged: Runnable) {
        if (this.uiTheme != uiTheme) {
            this.uiTheme = uiTheme
            initializeSchemeHandler(uiTheme)
            this.panel.browser.cefBrowser.reloadIgnoreCache()
            // promise needs to be reset, to that it can be listened to again when the reload is complete
            resetInitializedPromise()
            onThemeChanged.run()
        }
    }

    private var _initializedPromise = AsyncPromise<Unit>()

    // hide the internal promise type from the outside
    fun initialized(): Promise<Unit> {
        return _initializedPromise
    }

    private fun resetInitializedPromise() {
        _initializedPromise = AsyncPromise()
    }
}