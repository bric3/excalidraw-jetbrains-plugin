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
import com.github.bric3.excalidraw.files.ExcalidrawImageType.EXCALIDRAW
import com.github.bric3.excalidraw.files.ExcalidrawImageType.JPG
import com.github.bric3.excalidraw.files.ExcalidrawImageType.PNG
import com.github.bric3.excalidraw.files.ExcalidrawImageType.SVG
import com.github.bric3.excalidraw.files.ExcalidrawImageType.WEBP
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.swing.BorderFactory

class ExcalidrawWebViewController(
    parentDisposable: Disposable,
    val fileName: String,
    var uiTheme: String
) : Disposable {
    private var isDisposed: Boolean = false

    private val jcefPanel = LoadableJCEFHtmlPanel(
        parentDisposable = this,
        url = pluginUrl,
        openDevtools = debugMode
    )

    val component = jcefPanel.component

    private val correlatedResponseMapChannel = ConcurrentHashMap<String, Channel<String>>()

    private val debounceAutoSaveInMs = 1000

    private val _payload = MutableStateFlow<String?>(null)
    val payload: Flow<String?> = _payload

    private val _whenReady = MutableSharedFlow<Unit>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val whenReady: Flow<Unit> = _whenReady

    init {
        Disposer.register(parentDisposable, this)

        logger.debug {
            """
            Loading from alternate location:
            - webapp : $webappPath
            - excalidraw assets : $webappExcalidrawAssetsPath
            """.trimIndent()
        }

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

                    "continuous-update" -> _payload.value = message["content"]!!
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

                    "binary-image-base64-content" -> {
                        runBlocking {
                            channel?.send(message["base64Payload"]!!)
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
                    
                    window.initialProps = {
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
                    _whenReady.tryEmit(Unit)
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

    fun openDevTools() {
        jcefPanel.openDevTools()
    }

    fun loadJsonPayload(jsonPayload: String) {
        _payload.value = null

        runJS(
            "loadJsonPayload",
            """
            // Mark as raw String otherwise escape sequence are processed
            // https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Template_literals#raw_strings
            var json = JSON.parse(String.raw`$jsonPayload`);
            
            window.postMessage({
                type: "update",
                elements: json.elements,
                files: json.files
            }, 'https://$pluginDomain')
            """
        )
    }

    fun loadFromImageFile(file: VirtualFile) {
        _payload.value = null

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
            SVG -> "save-as-svg"
            PNG, JPG, WEBP -> "save-as-binary-image"
            EXCALIDRAW -> "save-as-json"
        }
        val mimeType = imageType.mimeType

        val saveOptionsJson = mapper.writeValueAsString(saveOptions)

        val correlationId = UUID.randomUUID().toString()
        val channel = Channel<String>(1)
        correlatedResponseMapChannel[correlationId] = channel
        logger.debug("$fileName: notify excalidraw to save content as $imageType ($mimeType), correlation-id: $correlationId")

        runJS(
            "saveAsCoroutines",
            """
            var json = JSON.parse(String.raw`$saveOptionsJson`)

            window.postMessage({
                type: "$msgType",
                mimeType: "$mimeType",
                exportConfig: json,
                correlationId: "$correlationId" 
            }, 'https://$pluginDomain')
            """
        )

        val fromJcefView = channel.receive()
        _payload.value = fromJcefView
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
        val logger = logger<ExcalidrawWebViewController>()

        private const val pluginDomain = "excalidraw-jetbrains-plugin"
        const val pluginUrl = "https://$pluginDomain/index.html"

        private val webappPath = System.getProperty("excalidraw.internal.webappPath", "UNSET")
        private val webappExcalidrawAssetsPath = System.getProperty("excalidraw.internal.webappExcalidrawAssetsPath", "UNSET")

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
                    logger.debug { "Request URI : ${uri.path}" }

                    // If the URI matches what is in the fsMapping, then the file is loaded from here
                    // Otherwise it is part of the Excalidraw app

                    // TODO Rewrite the fsMapping to not use the file name as key
                    val matchingFile = fsMapping.entries.firstOrNull { uri.path.endsWith(it.key) }

                    val devWebappFile = Path.of(webappPath, uri.path)
                    val devExcalidrawAssetFile = Path.of(webappExcalidrawAssetsPath, uri.path)

                    val inputStream = when {
                        matchingFile != null -> {
                            fsMapping - matchingFile.key

                            matchingFile.value.inputStream
                        }

                        Files.exists(devWebappFile) -> Files.newInputStream(devWebappFile)
                        Files.exists(devExcalidrawAssetFile) -> Files.newInputStream(devExcalidrawAssetFile)

                        else -> ExcalidrawWebViewController::class.java.getResourceAsStream("/assets${uri.path}")
                    }

                    inputStream?.let(::BufferedInputStream)
                }
            ).also { successful -> assert(successful) }
        }

        val isSupported = JBCefApp.isSupported()
    }
}
