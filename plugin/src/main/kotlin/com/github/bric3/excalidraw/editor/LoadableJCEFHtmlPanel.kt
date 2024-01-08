package com.github.bric3.excalidraw.editor

import com.github.bric3.excalidraw.debuggingLogWithThread
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
import com.intellij.ui.jcef.JCEFHtmlPanel
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
 * This file is copied <em>as is from</em>
 * <a href="https://github.com/docToolchain/diagrams.net-intellij-plugin/blob/14a4c8f7da38e5e4952e7046740493994f726e57/src/main/kotlin/de/docs_as_co/intellij/plugin/drawio/utils/LoadableJCEFHtmlPanel.kt">
 *     docToolchain/diagrams.net-intellij-plugin
 * </a>.
 *
 * Modified to support to open devtools programmatically on load.
 */
class LoadableJCEFHtmlPanel(
    url: String? = null, html: String? = null, openDevtools: Boolean = false,
    var timeoutCallback: String? = EditorBundle.message("message.html.editor.timeout")
) : Disposable {
    private val logger = thisLogger()
    private val htmlPanelComponent = JCEFHtmlPanel(null)
    // source of memory leak ?
    private val loadingPanel = JBLoadingPanel(BorderLayout(), this).apply { setLoadingText(CommonBundle.getLoadingTreeNodeText()) }
    private val alarm = Alarm()

    val browser: JBCefBrowser get() = htmlPanelComponent

    companion object {
        private const val LOADING_KEY = 1
        private const val CONTENT_KEY = 0
    }

    private val multiPanel: MultiPanel = object : MultiPanel() {
        override fun create(key: Int) = when (key) {
            LOADING_KEY -> loadingPanel
            CONTENT_KEY -> htmlPanelComponent.component
            else -> throw UnsupportedOperationException("Unknown key")
        }
    }

    init {
        Disposer.register(this, browser)
        if (url != null) {
            htmlPanelComponent.loadURL(url)
        }
        if (html != null) {
            htmlPanelComponent.loadHTML(html)
        }
        multiPanel.select(CONTENT_KEY, true)
    }

    init {
        htmlPanelComponent.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(browser: CefBrowser?, frame: CefFrame?, transitionType: CefRequest.TransitionType?) {
                if (openDevtools) {
                    this@LoadableJCEFHtmlPanel.browser.openDevtools()
                }
                alarm.addRequest({ htmlPanelComponent.setHtml(timeoutCallback!!) }, Registry.intValue("html.editor.timeout", 10000))
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                alarm.cancelAllRequests()
            }

            override fun onLoadingStateChange(browser: CefBrowser?, isLoading: Boolean, canGoBack: Boolean, canGoForward: Boolean) {
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
        }, htmlPanelComponent.cefBrowser)
    }

    override fun dispose() {
        debuggingLogWithThread(logger) { "LoadableJCEFHtmlPanel::dispose" }
        alarm.dispose()
    }

    val component: JComponent get() = this.multiPanel
}
