package com.github.bric3.excalidraw.editor

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.onTermination
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.BufferedInputStream
import java.net.URI

class ExcalidrawWebView(val lifetime: Lifetime, var uiTheme: String) {
    companion object {
        var didRegisterSchemeHandler = false
        fun initializeSchemeHandler() {
            didRegisterSchemeHandler = true

            // clear old scheme handler factories in case this is a re-initialization with an updated theme
            CefApp.getInstance().clearSchemeHandlerFactories()

            // initialization ideas from docToolchain/diagrams.net-intellij-plugin
            CefApp.getInstance().registerSchemeHandlerFactory(
                "https", "excalidraw-plugin",
                SchemeHandlerFactory { uri: URI ->
                    // special treatment for uri.path == /index.html ? Eg like tweaking the style.
                    BufferedInputStream(ExcalidrawWebView::class.java.getResourceAsStream("/assets" + uri.path))
                }
            ).also { successful -> assert(successful) }
        }
    }

    private val panel = LoadableJCEFHtmlPanel("https://excalidraw-plugin/index.html", null, null)
    val component = panel.component

    init {
        initializeSchemeHandler()
        object : CefLoadHandlerAdapter() {
            override fun onLoadStart(
                browser: CefBrowser?,
                frame: CefFrame?,
                transitionType: CefRequest.TransitionType?
            ) {
                // InitialData object {
                //  "elements" : [],
                //  "appState": {},
                //  "scrollToContent": true,
                //  libraryItems,
                //
                //  "readOnly"
                //  "gridMode"
                //  "zenMode"
                //  "theme"

                frame?.executeJavaScript(
                    """
                    window.EXCALIDRAW_ASSET_PATH = "/"; // loads assets from plugin
                    
                    window.initialData = {
                        "theme": "${uiTheme}",
                        "readOnly": false,
                        "gridMode": true,
                        "zenMode": false,
                    };
                    """.trimIndent(),
                    frame.url,
                    0
                )
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
            initializeSchemeHandler()
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