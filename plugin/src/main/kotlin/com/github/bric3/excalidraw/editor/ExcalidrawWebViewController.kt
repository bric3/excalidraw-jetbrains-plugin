package com.github.bric3.excalidraw.editor

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.SceneModes
import com.github.bric3.excalidraw.debugMode
import com.github.bric3.excalidraw.debuggingLogWithThread
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.io.BufferedInputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.swing.BorderFactory

class ExcalidrawWebViewController(
    private val parentDisposable: Disposable,
    val fileName: String,
    var uiTheme: String
) : Disposable {
    private var isDisposed: Boolean = false

    val logger = thisLogger()

    private val jcefPanel = LoadableJCEFHtmlPanel(
        parentDisposable = this,
        url = pluginUrl,
        openDevtools = debugMode
    )

    val component = jcefPanel.component

    private val correlatedResponseMapChannel = ConcurrentHashMap<String, Channel<String>>()

    private val debounceAutoSaveInMs = 1000

    val payload = MutableStateFlow<String?>(null)

    val whenReady = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        Disposer.register(parentDisposable, this)
        initJcefPanel()
    }

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
                debuggingLogWithThread(logger) { "CefMessageRouterHandlerAdapter::onQuery" }
                logger.debug { "$fileName disposed: ${isDisposed}, request: $request" }

                if (isDisposed) {
                    logger.debug("$fileName: disposed")
                    return false
                }


                val message = mapper.readValue<Map<String, String>>(request!!)
                val channel = correlatedResponseMapChannel.remove(message["correlationId"] ?: "")

                when (message["type"]) {
                    "ready" -> { /* no-op: reason using Excalidraw callback/readiness seems less reliable than onLoadEnd */
                    }

                    "continuous-update" -> payload.value = message["content"]!!
                    "json-content" -> {
                        runBlocking {
                            channel?.send(message["json"]!!)
                            channel?.close()
                        }
                    }

                    "svg-content" -> {
                        runBlocking {
                            channel?.send(message["svg"]!!)
                            channel?.close()
                        }
                    }

                    "png-base64-content" -> {
                        runBlocking {
                            channel?.send(message["png"]!!)
                            channel?.close()
                        }
                    }

                    else -> logger.error("Unrecognized message request from excalidraw : $request")
                }

                return true
            }
        }.also { routerHandler ->
            messageRouter.addHandler(routerHandler, true)
            jcefPanel.jbCefBrowser.jbCefClient.cefClient.addMessageRouter(messageRouter)
            Disposer.register(this) {
                logger.debug("$fileName: removing message router")
                jcefPanel.jbCefBrowser.jbCefClient.cefClient.removeMessageRouter(messageRouter)
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
                    // language=JavaScript
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
                    whenReady.tryEmit(Unit)
                }
            }
        }.also { loadHandler ->
            jcefPanel.jbCefBrowser.jbCefClient.addLoadHandler(loadHandler, jcefPanel.jbCefBrowser.cefBrowser)
            Disposer.register(this) {
                logger.debug("$fileName: removing load handler")
                jcefPanel.jbCefBrowser.jbCefClient.removeLoadHandler(loadHandler, jcefPanel.jbCefBrowser.cefBrowser)
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
                    logger.warn("$fileName: Some of required message values were null!")
                    logger.warn("$fileName: level: $level source: $source:$line\n\tmessage: $message")
                } else {
                    val formattedMessage = "$fileName: [$level][$source:$line]:\n${message}"

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
            jcefPanel.jbCefBrowser.jbCefClient.addDisplayHandler(displayHandler, jcefPanel.jbCefBrowser.cefBrowser)
            Disposer.register(this) {
                logger.debug("$fileName: removing display handler")
                jcefPanel.jbCefBrowser.jbCefClient.removeDisplayHandler(
                    displayHandler,
                    jcefPanel.jbCefBrowser.cefBrowser
                )
            }
        }
    }

    fun loadJsonPayload(jsonPayload: String) {
        payload.value = null

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

    fun loadFromImageFile(file: VirtualFile) {
        payload.value = null

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

    suspend fun triggerSnapshot(imageType: ExcalidrawImageType, saveOptions: SaveOptions?): String {
        debuggingLogWithThread(logger) { "ExcalidrawWebViewController::saveAsCoroutines" }

        val msgType = when (imageType) {
            ExcalidrawImageType.SVG -> "save-as-svg"
            ExcalidrawImageType.PNG -> "save-as-png"
            ExcalidrawImageType.EXCALIDRAW -> "save-as-json"
        }

        val saveOptionsJson = mapper.writeValueAsString(saveOptions)

        val correlationId = UUID.randomUUID().toString()
        val channel = Channel<String>(1)
        correlatedResponseMapChannel[correlationId] = channel
        logger.debug("$fileName: notify excalidraw to save content as $imageType, correlation-id: $correlationId")

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

        val fromJcefView = channel.receive()
        payload.value = fromJcefView
        return fromJcefView
    }

    private fun runJS(jsOperation: String, @Language("JavaScript") js: String) {
        if (isDisposed) {
            logger.warn("$fileName: runJS: controller is disposed: $jsOperation")
            return
        }
        val mainFrame = jcefPanel.jbCefBrowser.cefBrowser.mainFrame
        if (mainFrame == null) {
            logger.warn("$fileName: runJS: mainFrame is null for operation: $jsOperation")
            return
        }

        mainFrame.executeJavaScript(
            js.trimIndent(),
            mainFrame.url,
            0
        )
    }

    override fun dispose() {
        isDisposed = true
    }

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

        val isSupported = JBCefApp.isSupported()
    }
}
