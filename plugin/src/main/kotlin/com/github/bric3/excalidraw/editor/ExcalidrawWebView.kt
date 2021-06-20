package com.github.bric3.excalidraw.editor

import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.assertAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
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
                    println(uri)
                    BufferedInputStream(ExcalidrawWebView::class.java.getResourceAsStream("/assets" + uri.path))
                }
            ).also { successful -> assert(successful) }
        }
    }

    private val panel = LoadableJCEFHtmlPanel(
        url = "https://excalidraw-plugin/index.html",
        openDevtools = true
    )
    val component = panel.component

    init {
        initializeSchemeHandler()

//        val jbCefJSQuery = JBCefJSQuery.create(panel.browser)
//        jbCefJSQuery.addHandler { request: String ->
//            println(request)
//
//            null
//        };
//

        val messageRouter = CefMessageRouter.create()
        messageRouter.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?
            ): Boolean {
                println(request)

                // {"type":"svg-content","svg":"<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 46\" width=\"64\" height=\"46\">\n  <!-- svg-source:excalidraw -->\n  \n  <defs>\n    <style>\n      @font-face {\n        font-family: \"Virgil\";\n        src: url(\"https://excalidraw.com/Virgil.woff2\");\n      }\n      @font-face {\n        font-family: \"Cascadia\";\n        src: url(\"https://excalidraw.com/Cascadia.woff2\");\n      }\n    </style>\n  </defs>\n  <rect x=\"0\" y=\"0\" width=\"64\" height=\"46\" fill=\"#ffffff\"></rect><g transform=\"translate(10 10) rotate(0 22 13)\"><text x=\"0\" y=\"18\" font-family=\"Virgil, Segoe UI Emoji\" font-size=\"20px\" fill=\"#000000\" text-anchor=\"start\" style=\"white-space: pre;\" direction=\"ltr\">Hello</text></g></svg>"}

                return true
            }
        }, true)
        panel.browser.jbCefClient.cefClient.addMessageRouter(messageRouter)

//        postMessage({ type:"save-as-svg", exportConfig: {} })


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
                // Properties
                //  "readOnly"
                //  "gridMode"
                //  "zenMode"
                //  "theme"
                //
                // initialData : https://github.com/excalidraw/excalidraw/tree/master/src/packages/excalidraw#initialdata
                // properties https://github.com/excalidraw/excalidraw/tree/master/src/packages/excalidraw#props

                // updateScene
                //
                // https://github.com/excalidraw/excalidraw/blob/5cd921549a7e2b67219ee3f10d98228e23103c0f/src/types.ts#L207-L212

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

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                if (frame?.url == "https://excalidraw-plugin/index.html") {
                    _initializedPromise.setResult(Unit)
                }
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
            _initializedPromise = AsyncPromise()
            onThemeChanged.run()
        }
    }

    private var _initializedPromise = AsyncPromise<Unit>()

    // hide the internal promise type from the outside
    fun initialized(): Promise<Unit> {
        return _initializedPromise
    }


    // Usage inspired by diagrams.net integration plugin
    // usage of the reactive distributed framework to communicate changes
    private val _excalidrawPayload = Property<String?>(null)
    val excalidrawPayload: IPropertyView<String?> = _excalidrawPayload


    fun loadJsonPayload(jsonPayload: String) {

        _excalidrawPayload.set(null)

        lifetime.assertAlive()

        // TODO use restore instead
//        const result = restore(
//                {
//                    elements: clearElementsForExport(data.elements || []),
//                    appState: {
//                    theme: localAppState?.theme,
//                    fileHandle: (!blob.type.startsWith("image/") && blob.handle) || null,
//                    ...cleanAppStateForExport(data.appState || {}),
//                    ...(localAppState
//                    ? calculateScrollCenter(data.elements || [], localAppState, null)
//                    : {}),
//                },
//                },
//        localAppState,
//        );

        // Json Validation ?
        panel.browser.cefBrowser.mainFrame.executeJavaScript(
            """
            var json = JSON.parse(`$jsonPayload`);
            
            window.postMessage({
                type: "update",
                elements: json.elements
            })
            """.trimIndent(),
            panel.browser.cefBrowser.mainFrame.url,
            0
        )
    }

    fun makeReadOnly() {
        lifetime.assertAlive()
        panel.browser.cefBrowser.mainFrame.executeJavaScript(
            """
            window.postMessage({
                type: "read-only"
            })
            """.trimIndent(),
            panel.browser.cefBrowser.mainFrame.url,
            0
        )
    }
}