package com.github.bric3.excalidraw.editor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.assertAlive
import com.jetbrains.rd.util.lifetime.isAlive
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
import org.intellij.lang.annotations.Language
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.BufferedInputStream
import java.net.URI

class ExcalidrawWebView(val lifetime: Lifetime, var uiTheme: String) {
    companion object {
        private const val pluginDomain = "excalidraw-jetbrains-plugin"
        const val pluginUrl = "https://$pluginDomain/index.html"

        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        }

        var didRegisterSchemeHandler = false
        fun initializeSchemeHandler() {
            didRegisterSchemeHandler = true

            // clear old scheme handler factories in case this is a re-initialization with an updated theme
            CefApp.getInstance().clearSchemeHandlerFactories()

            // initialization ideas from docToolchain/diagrams.net-intellij-plugin
            CefApp.getInstance().registerSchemeHandlerFactory(
                "https", pluginDomain,
                SchemeHandlerFactory { uri: URI ->
                    BufferedInputStream(ExcalidrawWebView::class.java.getResourceAsStream("/assets" + uri.path))
                }
            ).also { successful -> assert(successful) }
        }
    }

    private val panel = LoadableJCEFHtmlPanel(
        url = pluginUrl,
        openDevtools = false
    )
    val component = panel.component

    init {
        initializeSchemeHandler()

        val messageRouter = CefMessageRouter.create()
        object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?
            ): Boolean {
//                println("lifetime: ${lifetime.isAlive}, request: $request")

                val message = mapper.readValue<Map<String, String>>(request!!)
                
                if (!lifetime.isAlive) return false


                when (message["type"]) {
                    "ready" -> { /* no op : reason using Excalidraw callback/readiness seems less reliable than onLoadEnd */ }

                    // {"type":"continuous-update","content":"{\n  \"type\": \"excalidraw\",\n  \"version\": 2,\n  \"source\": \"https://excalidraw-plugin\",\n  \"elements\": [\n    {\n      \"id\": \"iXnxxJATdZI9GNSKXAq5o\",\n      \"type\": \"text\",\n      \"x\": 280,\n      \"y\": 180,\n      \"width\": 44,\n      \"height\": 26,\n      \"angle\": 0,\n      \"strokeColor\": \"#000000\",\n      \"backgroundColor\": \"transparent\",\n      \"fillStyle\": \"hachure\",\n      \"strokeWidth\": 1,\n      \"strokeStyle\": \"solid\",\n      \"roughness\": 1,\n      \"opacity\": 100,\n      \"groupIds\": [],\n      \"strokeSharpness\": \"sharp\",\n      \"seed\": 415262735,\n      \"version\": 29,\n      \"versionNonce\": 191228684,\n      \"isDeleted\": false,\n      \"boundElementIds\": null,\n      \"text\": \"Hello\",\n      \"fontSize\": 20,\n      \"fontFamily\": 1,\n      \"textAlign\": \"left\",\n      \"verticalAlign\": \"top\",\n      \"baseline\": 18\n    }\n  ],\n  \"appState\": {\n    \"gridSize\": 20,\n    \"viewBackgroundColor\": \"#ffffff\"\n  }\n}"}
                    "continuous-update" -> _excalidrawPayload.set(message["content"]!!)
                    // {"type":"json-content","json":"{\n  \"type\": \"excalidraw\",\n  \"version\": 2,\n  \"source\": \"https://excalidraw-plugin\",\n  \"elements\": [\n    {\n      \"id\": \"iXnxxJATdZI9GNSKXAq5o\",\n      \"type\": \"text\",\n      \"x\": 280,\n      \"y\": 180,\n      \"width\": 44,\n      \"height\": 26,\n      \"angle\": 0,\n      \"strokeColor\": \"#000000\",\n      \"backgroundColor\": \"transparent\",\n      \"fillStyle\": \"hachure\",\n      \"strokeWidth\": 1,\n      \"strokeStyle\": \"solid\",\n      \"roughness\": 1,\n      \"opacity\": 100,\n      \"groupIds\": [],\n      \"strokeSharpness\": \"sharp\",\n      \"seed\": 415262735,\n      \"version\": 29,\n      \"versionNonce\": 191228684,\n      \"isDeleted\": false,\n      \"boundElementIds\": null,\n      \"text\": \"Hello\",\n      \"fontSize\": 20,\n      \"fontFamily\": 1,\n      \"textAlign\": \"left\",\n      \"verticalAlign\": \"top\",\n      \"baseline\": 18\n    }\n  ],\n  \"appState\": {\n    \"gridSize\": 20,\n    \"viewBackgroundColor\": \"#ffffff\"\n  }\n}"}
                    "json-content" -> _excalidrawPayload.set(message["json"]!!)

                    // TODO SVG / PNG export
                    // {"type":"svg-content","svg":"<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 46\" width=\"64\" height=\"46\">\n  <!-- svg-source:excalidraw -->\n  \n  <defs>\n    <style>\n      @font-face {\n        font-family: \"Virgil\";\n        src: url(\"https://excalidraw.com/Virgil.woff2\");\n      }\n      @font-face {\n        font-family: \"Cascadia\";\n        src: url(\"https://excalidraw.com/Cascadia.woff2\");\n      }\n    </style>\n  </defs>\n  <rect x=\"0\" y=\"0\" width=\"64\" height=\"46\" fill=\"#ffffff\"></rect><g transform=\"translate(10 10) rotate(0 22 13)\"><text x=\"0\" y=\"18\" font-family=\"Virgil, Segoe UI Emoji\" font-size=\"20px\" fill=\"#000000\" text-anchor=\"start\" style=\"white-space: pre;\" direction=\"ltr\">Hello</text></g></svg>"}
                    "svg-content" -> println("SVG content: ${message["svg"]!!}")
                    "png-content" -> println("PNG Base 64 content: ${message["png-base64"]!!}")
                    else -> println("Unrecognized message request from excalidraw : $request")
                }

                return true
            }
        }.also { routerHandler ->
            messageRouter.addHandler(routerHandler, true)
            panel.browser.jbCefClient.cefClient.addMessageRouter(messageRouter)
            lifetime.onTermination {
                panel.browser.jbCefClient.cefClient.removeMessageRouter(messageRouter)
                messageRouter.dispose()
            }
        }

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
                if (frame?.url == pluginUrl) {
                    _initializedPromise.setResult(Unit)
                }
            }
        }.also { loadHandler ->
            panel.browser.jbCefClient.addLoadHandler(loadHandler, panel.browser.cefBrowser)
            lifetime.onTermination {
                panel.browser.jbCefClient.removeLoadHandler(loadHandler, panel.browser.cefBrowser)
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

        runJS(
            """
            var json = JSON.parse(`$jsonPayload`);
            
            window.postMessage({
                type: "update",
                elements: json.elements
            })
            """
        )
    }

    fun toggleReadOnly(readOnly: Boolean) {
        runJS(
            """
            window.postMessage({
                type: "toggle-read-only",
                readOnly: $readOnly
            })
            """
        )
    }

    fun changeTheme(theme: String) {
        runJS(
            """
            window.postMessage({
                type: "theme-change",
                theme: "$theme"
            })
            """
        )
    }

    private fun runJS(@Language("JavaScript") js: String) {
        lifetime.assertAlive()
        panel.browser.cefBrowser.mainFrame.executeJavaScript(
            js.trimIndent(),
            panel.browser.cefBrowser.mainFrame.url,
            0
        )
    }
}