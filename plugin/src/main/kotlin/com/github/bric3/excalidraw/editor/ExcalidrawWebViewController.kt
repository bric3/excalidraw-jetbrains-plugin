package com.github.bric3.excalidraw.editor

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.intellij.openapi.diagnostic.thisLogger
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.assertAlive
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import org.cef.CefApp
import org.cef.CefSettings
import org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR
import org.cef.CefSettings.LogSeverity.LOGSEVERITY_FATAL
import org.cef.CefSettings.LogSeverity.LOGSEVERITY_INFO
import org.cef.CefSettings.LogSeverity.LOGSEVERITY_VERBOSE
import org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefMessageRouterHandlerAdapter
import org.cef.network.CefRequest
import org.intellij.lang.annotations.Language
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import java.io.BufferedInputStream
import java.net.URI
import java.util.*
import java.util.concurrent.*
import javax.swing.BorderFactory

class ExcalidrawWebViewController(val lifetime: Lifetime, var uiTheme: String) {
    val logger = thisLogger()
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
                    BufferedInputStream(ExcalidrawWebViewController::class.java.getResourceAsStream("/assets" + uri.path))
                }
            ).also { successful -> assert(successful) }
        }
    }

    val jcefPanel = LoadableJCEFHtmlPanel(
        url = pluginUrl,
        openDevtools = false
    )
    val component = jcefPanel.component

    private val correlatedResponseMap = ConcurrentHashMap<String, AsyncPromise<String>>()

    init {
        initJcefPanel()
    }

    private val debounceAutoSaveInMs = 1000

    private fun initJcefPanel() {
        initializeSchemeHandler()

        // the bigger border allows the mouse tab resize handle to be not so picky.
        jcefPanel.component.border = BorderFactory.createEmptyBorder(2, 2, 2, 2)

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
                if (logger.isDebugEnabled) {
                    logger.debug("lifetime: ${lifetime.isAlive}, request: $request")
                }

                if (!lifetime.isAlive) {
                    logger.debug("not alive")
                    return false
                }


                val message = mapper.readValue<Map<String, String>>(request!!)
                when (message["type"]) {
                    "ready" -> { /* no op : reason using Excalidraw callback/readiness seems less reliable than onLoadEnd */ }

                    // {"type":"continuous-update","content":"{\n  \"type\": \"excalidraw\",\n  \"version\": 2,\n  \"source\": \"https://excalidraw-plugin\",\n  \"elements\": [\n    {\n      \"id\": \"iXnxxJATdZI9GNSKXAq5o\",\n      \"type\": \"text\",\n      \"x\": 280,\n      \"y\": 180,\n      \"width\": 44,\n      \"height\": 26,\n      \"angle\": 0,\n      \"strokeColor\": \"#000000\",\n      \"backgroundColor\": \"transparent\",\n      \"fillStyle\": \"hachure\",\n      \"strokeWidth\": 1,\n      \"strokeStyle\": \"solid\",\n      \"roughness\": 1,\n      \"opacity\": 100,\n      \"groupIds\": [],\n      \"strokeSharpness\": \"sharp\",\n      \"seed\": 415262735,\n      \"version\": 29,\n      \"versionNonce\": 191228684,\n      \"isDeleted\": false,\n      \"boundElementIds\": null,\n      \"text\": \"Hello\",\n      \"fontSize\": 20,\n      \"fontFamily\": 1,\n      \"textAlign\": \"left\",\n      \"verticalAlign\": \"top\",\n      \"baseline\": 18\n    }\n  ],\n  \"appState\": {\n    \"gridSize\": 20,\n    \"viewBackgroundColor\": \"#ffffff\"\n  }\n}"}
                    "continuous-update" -> _excalidrawPayload.set(message["content"]!!)
                    // {"type":"json-content","json":"{\n  \"type\": \"excalidraw\",\n  \"version\": 2,\n  \"source\": \"https://excalidraw-plugin\",\n  \"elements\": [\n    {\n      \"id\": \"iXnxxJATdZI9GNSKXAq5o\",\n      \"type\": \"text\",\n      \"x\": 280,\n      \"y\": 180,\n      \"width\": 44,\n      \"height\": 26,\n      \"angle\": 0,\n      \"strokeColor\": \"#000000\",\n      \"backgroundColor\": \"transparent\",\n      \"fillStyle\": \"hachure\",\n      \"strokeWidth\": 1,\n      \"strokeStyle\": \"solid\",\n      \"roughness\": 1,\n      \"opacity\": 100,\n      \"groupIds\": [],\n      \"strokeSharpness\": \"sharp\",\n      \"seed\": 415262735,\n      \"version\": 29,\n      \"versionNonce\": 191228684,\n      \"isDeleted\": false,\n      \"boundElementIds\": null,\n      \"text\": \"Hello\",\n      \"fontSize\": 20,\n      \"fontFamily\": 1,\n      \"textAlign\": \"left\",\n      \"verticalAlign\": \"top\",\n      \"baseline\": 18\n    }\n  ],\n  \"appState\": {\n    \"gridSize\": 20,\n    \"viewBackgroundColor\": \"#ffffff\"\n  }\n}"}
                    "json-content" -> _excalidrawPayload.set(message["json"]!!)

                    // {"type":"svg-content","svg":"<svg version=\"1.1\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 64 46\" width=\"64\" height=\"46\">\n  <!-- svg-source:excalidraw -->\n  \n  <defs>\n    <style>\n      @font-face {\n        font-family: \"Virgil\";\n        src: url(\"https://excalidraw.com/Virgil.woff2\");\n      }\n      @font-face {\n        font-family: \"Cascadia\";\n        src: url(\"https://excalidraw.com/Cascadia.woff2\");\n      }\n    </style>\n  </defs>\n  <rect x=\"0\" y=\"0\" width=\"64\" height=\"46\" fill=\"#ffffff\"></rect><g transform=\"translate(10 10) rotate(0 22 13)\"><text x=\"0\" y=\"18\" font-family=\"Virgil, Segoe UI Emoji\" font-size=\"20px\" fill=\"#000000\" text-anchor=\"start\" style=\"white-space: pre;\" direction=\"ltr\">Hello</text></g></svg>"}
                    "svg-content" -> {
                        val promise = correlatedResponseMap.remove(message["correlationId"] ?: "")
                        promise?.setResult(message["svg"])
                    }
                    "png-base64-content" -> {
                        val promise = correlatedResponseMap.remove(message["correlationId"] ?: "")
                        promise?.setResult(message["png"])
                    }
                    else -> println("Unrecognized message request from excalidraw : $request")
                }

                return true
            }
        }.also { routerHandler ->
            messageRouter.addHandler(routerHandler, true)
            jcefPanel.browser.jbCefClient.cefClient.addMessageRouter(messageRouter)
            lifetime.onTermination {
                jcefPanel.browser.jbCefClient.cefClient.removeMessageRouter(messageRouter)
                messageRouter.dispose()
            }
        }

        object : CefLoadHandlerAdapter() {
            override fun onLoadStart(
                browser: CefBrowser?,
                frame: CefFrame?,
                transitionType: CefRequest.TransitionType?
            ) {
                // initialData : https://github.com/excalidraw/excalidraw/tree/master/src/packages/excalidraw#initialdata
                // properties https://github.com/excalidraw/excalidraw/tree/master/src/packages/excalidraw#props

                frame?.executeJavaScript(
                    """
                    window.EXCALIDRAW_ASSET_PATH = "/"; // loads assets from plugin
                    
                    window.initialData = {
                        "theme": "$uiTheme",
                        "readOnly": false,
                        "gridMode": false,
                        "zenMode": false,
                        "debounceAutoSaveInMs": ${this@ExcalidrawWebViewController.debounceAutoSaveInMs}
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
            jcefPanel.browser.jbCefClient.addLoadHandler(loadHandler, jcefPanel.browser.cefBrowser)
            lifetime.onTermination {
                jcefPanel.browser.jbCefClient.removeLoadHandler(loadHandler, jcefPanel.browser.cefBrowser)
            }
        }

        object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                browser: CefBrowser?,
                level: CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int
            ): Boolean {
                if (level == null || message == null || source == null) {
                    logger.warn("Some of required message values were null!")
                    logger.warn("level: $level source: $source:$line\n\tmessage: $message")
                } else {
                    val formattedMessage = "[$level][$source:$line]:\n${message}"

                    when (level) {
                        LOGSEVERITY_ERROR, LOGSEVERITY_FATAL -> logger.error(formattedMessage)
                        LOGSEVERITY_INFO -> logger.info(formattedMessage)
                        LOGSEVERITY_WARNING -> logger.warn(formattedMessage)
                        LOGSEVERITY_VERBOSE -> logger.debug(formattedMessage)
                        else -> logger.info(formattedMessage)
                    }
                }
                return super.onConsoleMessage(browser, level, message, source, line)
            }
        }.also { displayHandler ->
            jcefPanel.browser.jbCefClient.addDisplayHandler(displayHandler, jcefPanel.browser.cefBrowser)
            lifetime.onTermination {
                jcefPanel.browser.jbCefClient.removeDisplayHandler(displayHandler, jcefPanel.browser.cefBrowser)
            }
        }
    }

    // Usage inspired by diagrams.net integration plugin
    // usage of the reactive distributed framework to communicate changes
    private val _excalidrawPayload = Property<String?>(null)
    val excalidrawPayload: IPropertyView<String?> = _excalidrawPayload

    private var _initializedPromise = AsyncPromise<Unit>()
    fun initialized(): Promise<Unit> {
        return _initializedPromise
    }


    fun loadJsonPayload(jsonPayload: String) {
        _excalidrawPayload.set(null)

        runJS(
            """
            // Mark as raw String otherwise escape sequence are processed
            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#raw_strings
            var json = JSON.parse(String.raw`$jsonPayload`);
            
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

    fun saveAs(imageType: ExcalidrawImageType) : AsyncPromise<String> {
        val msgType = when (imageType) {
            ExcalidrawImageType.SVG -> "save-as-svg"
            ExcalidrawImageType.PNG -> "save-as-png"
        }
        val correlationId = UUID.randomUUID().toString()
        val payloadPromise = AsyncPromise<String>()
        correlatedResponseMap[correlationId] = payloadPromise

        runJS(
            """
            window.postMessage({
                type: "$msgType",
                exportConfig: {},
                correlationId: "$correlationId" 
            })
            """
        )

        return payloadPromise
    }

    private fun runJS(@Language("JavaScript") js: String) {
        lifetime.assertAlive()
        jcefPanel.browser.cefBrowser.mainFrame.executeJavaScript(
            js.trimIndent(),
            jcefPanel.browser.cefBrowser.mainFrame.url,
            0
        )
    }
}