package com.github.bric3.excalidraw.editor

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.SceneModes
import com.github.bric3.excalidraw.debugMode
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.debuggingLogWithThread
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.rd.util.lifetime.Lifetime
import com.jetbrains.rd.util.lifetime.isAlive
import com.jetbrains.rd.util.lifetime.isNotAlive
import com.jetbrains.rd.util.lifetime.onTermination
import com.jetbrains.rd.util.reactive.IPropertyView
import com.jetbrains.rd.util.reactive.Property
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
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
import java.util.*
import java.util.concurrent.*
import javax.swing.BorderFactory

class ExcalidrawWebViewController(val lifetime: Lifetime, var uiTheme: String) : Disposable {
    val logger = thisLogger()

    companion object {
        private const val pluginDomain = "excalidraw-jetbrains-plugin"
        const val pluginUrl = "https://$pluginDomain/index.html"

        val mapper = jacksonObjectMapper().apply {
            configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            setSerializationInclusion(JsonInclude.Include.NON_NULL)
        }

        private val fsMapping = ConcurrentHashMap<String, VirtualFile>()

        private var didRegisterSchemeHandler = false
        fun initializeSchemeHandler() {
            didRegisterSchemeHandler = true

            // clear old scheme handler factories in case this is a re-initialization with an updated theme
            CefApp.getInstance().clearSchemeHandlerFactories()

            // initialization ideas from docToolchain/diagrams.net-intellij-plugin
            @Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
            CefApp.getInstance().registerSchemeHandlerFactory(
                "https", pluginDomain,
                SchemeHandlerFactory { uri ->
                    val matchingFile = fsMapping.entries.firstOrNull { uri.path.endsWith(it.key) }
                    val stream = matchingFile?.value?.inputStream

                    if (matchingFile != null) {
                        fsMapping - matchingFile.key
                    }

                    BufferedInputStream(
                        stream ?: ExcalidrawWebViewController::class.java.getResourceAsStream("/assets${uri.path}")
                    )
                }
            ).also { successful -> assert(successful) }
        }
    }

    val jcefPanel = LoadableJCEFHtmlPanel(
        url = pluginUrl,
        openDevtools = debugMode
    )
    val component = jcefPanel.component

    private val correlatedResponseMapChannel = ConcurrentHashMap<String, Channel<String>>()

    init {
        initJcefPanel()
    }

    private val debounceAutoSaveInMs = 1000

    private fun initJcefPanel() {
        initializeSchemeHandler()
        Disposer.register(this, jcefPanel)

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
                debuggingLogWithThread { "CefMessageRouterHandlerAdapter::onQuery" }
                if (logger.isDebugEnabled) {
                    logger.debug("lifetime alive: ${lifetime.isAlive}, request: $request")
                }

                if (!lifetime.isAlive) {
                    logger.debug("not alive")
                    return false
                }


                val message = mapper.readValue<Map<String, String>>(request!!)
                when (message["type"]) {
                    "ready" -> { /* no op : reason using Excalidraw callback/readiness seems less reliable than onLoadEnd */ }

                    "continuous-update" -> _excalidrawPayload.set(message["content"]!!)
                    "json-content" -> {
                        val channel = correlatedResponseMapChannel.remove(message["correlationId"] ?: "")
                        runBlocking {
                            channel?.send(message["json"]!!)
                        }

                    }
                    "svg-content" -> {
                        val channel = correlatedResponseMapChannel.remove(message["correlationId"] ?: "")
                        runBlocking {
                            channel?.send(message["svg"]!!)
                        }
                    }
                    "png-base64-content" -> {
                        val channel = correlatedResponseMapChannel.remove(message["correlationId"] ?: "")
                        runBlocking {
                            channel?.send(message["png"]!!)
                        }
                    }
                    else -> logger.error("Unrecognized message request from excalidraw : $request")
                }

                return true
            }
        }.also { routerHandler ->
            messageRouter.addHandler(routerHandler, true)
            jcefPanel.browser.jbCefClient.cefClient.addMessageRouter(messageRouter)
            lifetime.onTermination {
                logger.debug("removing message router")
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
                    window.EXCALIDRAW_ASSET_PATH = "/"; // loads excalidraw assets from plugin (instead of CDN)
                    
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
            "loadJsonPayload",
            """
            // Mark as raw String otherwise escape sequence are processed
            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#raw_strings
            var json = JSON.parse(String.raw`$jsonPayload`);
            
            window.postMessage({
                type: "update",
                elements: json.elements
            }, 'https://$pluginDomain')
            """
        )
    }

    fun loadFromFile(file: VirtualFile) {
        _excalidrawPayload.set(null)

        fsMapping[file.name] = file

        runJS(
            "loadFromFile",
            """
            // Mark as raw String otherwise escape sequence are processed
            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#raw_strings
            window.postMessage({
                type: "load-from-file",
                fileToFetch: String.raw`${file.name}`
            }, 'https://$pluginDomain')
            """
        )
    }

    fun toggleReadOnly(readOnly: Boolean) {
        runJS(
            "toggleReadOnly",
            """
            window.postMessage({
                type: "toggle-read-only",
                readOnly: $readOnly
            }, 'https://$pluginDomain')
            """
        )
    }

    fun toggleModes(sceneModes: SceneModes) {
        val sceneModesJson = mapper.writeValueAsString(sceneModes)

        runJS(
            "toggleModes",
            """
            var json = JSON.parse(String.raw`$sceneModesJson`)

            window.postMessage({
                type: "toggle-scene-modes",
                sceneModes: json
            }, 'https://$pluginDomain')
            """
        )
    }

    fun changeTheme(theme: String) {
        runJS(
            "changeTheme",
            """
            window.postMessage({
                type: "theme-change",
                theme: "$theme"
            }, 'https://$pluginDomain')
            """
        )
    }

    suspend fun saveAsCoroutines(imageType: ExcalidrawImageType, saveOptions: SaveOptions?): String {
        debuggingLogWithThread { "ExcalidrawWebViewController::saveAsCoroutines" }

        val msgType = when (imageType) {
            ExcalidrawImageType.SVG -> "save-as-svg"
            ExcalidrawImageType.PNG -> "save-as-png"
            ExcalidrawImageType.EXCALIDRAW -> "save-as-json"
        }

        val saveOptionsJson = mapper.writeValueAsString(saveOptions)

        val correlationId = UUID.randomUUID().toString()
        val channel = Channel<String>()
        correlatedResponseMapChannel[correlationId] = channel
        logger.debug("notify excalidraw to save content as $imageType, correlation-id: $correlationId")

        runJS(
            "saveAsCoroutines",
            """
            var json = JSON.parse(String.raw`$saveOptionsJson`)

            window.postMessage({
                type: "$msgType",
                exportConfig: json,
                correlationId: "$correlationId" 
            }, 'https://$pluginDomain')
            """
        )

        return channel.receive()
    }

    private fun runJS(jsOperation: String, @Language("JavaScript") js: String) {
        if (lifetime.isNotAlive) {
            thisLogger().warn("runJS: lifetime is not alive for operation: $jsOperation")
            return
        }
        jcefPanel.browser.cefBrowser.mainFrame.executeJavaScript(
            js.trimIndent(),
            jcefPanel.browser.cefBrowser.mainFrame.url,
            0
        )
    }

    override fun dispose() {
    }
}
