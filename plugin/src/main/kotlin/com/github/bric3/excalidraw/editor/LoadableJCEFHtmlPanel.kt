package com.github.bric3.excalidraw.editor

import com.intellij.CommonBundle
import com.intellij.ide.plugins.MultiPanel
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.EditorBundle
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.network.CefRequest
import java.awt.BorderLayout
import javax.swing.JComponent

/**
 * Creates a JCEF Panel
 *
 * This file is inspired from
 * [docToolchain/diagrams.net-intellij-plugin](https://github.com/docToolchain/diagrams.net-intellij-plugin/blob/14a4c8f7da38e5e4952e7046740493994f726e57/src/main/kotlin/de/docs_as_co/intellij/plugin/drawio/utils/LoadableJCEFHtmlPanel.kt).
 *
 * Modified to support to open devtools programmatically on load.
 */
class LoadableJCEFHtmlPanel(
    parentDisposable: Disposable,
    url: String? = null,
    html: String? = null,
    openDevtools: Boolean = false,
    var timeoutCallback: String? = EditorBundle.message("message.html.editor.timeout")
) : Disposable {
    private val logger = thisLogger()

    val jbCefBrowser = JBCefBrowser.createBuilder()
        .setEnableOpenDevToolsMenuItem(openDevtools)
        .setOffScreenRendering(useOsr)
        .build()

    private val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply {
        setLoadingText(CommonBundle.getLoadingTreeNodeText())
    }

    private val alarm = Alarm(this)

    private val multiPanel: MultiPanel = object : MultiPanel() {
        override fun create(key: Int) = when (key) {
            LOADING_KEY -> loadingPanel
            CONTENT_KEY -> jbCefBrowser.component
            else -> throw UnsupportedOperationException("Unknown key")
        }
    }

    init {
        Disposer.register(parentDisposable, this)
        Disposer.register(this, jbCefBrowser)
        if (url != null) {
            jbCefBrowser.loadURL(url)
        }
        if (html != null) {
            jbCefBrowser.loadHTML(html)
        }
        multiPanel.select(CONTENT_KEY, true)
    }

    init {
        jbCefBrowser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(
                browser: CefBrowser?,
                frame: CefFrame?,
                transitionType: CefRequest.TransitionType?
            ) {
                if (openDevtools) {
                    jbCefBrowser.openDevtools()
                }
                alarm.addRequest(
                    { jbCefBrowser.loadHTML(timeoutCallback!!) },
                    Registry.intValue("html.editor.timeout", 10000)
                )
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                alarm.cancelAllRequests()
            }

            override fun onLoadingStateChange(
                browser: CefBrowser?,
                isLoading: Boolean,
                canGoBack: Boolean,
                canGoForward: Boolean
            ) {
                if (isLoading) {
                    invokeLater {
                        loadingPanel.startLoading()
                        multiPanel.select(LOADING_KEY, true)
                    }
                } else {
                    invokeLater {
                        loadingPanel.stopLoading()
                        multiPanel.select(CONTENT_KEY, true)
                    }
                }
            }
        }, jbCefBrowser.cefBrowser)
    }

    fun openDevTools() {
        jbCefBrowser.openDevtools()
    }

    val component: JComponent get() = this.multiPanel

    override fun dispose() = Unit

    companion object {
        private const val LOADING_KEY = 1
        private const val CONTENT_KEY = 0
        private val useOsr
            get() = Registry.`is`("excalidraw.viewer.use.jcef.osr.view")
    }
}
