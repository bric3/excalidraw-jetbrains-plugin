package com.github.bric3.excalidraw.editor

import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.notifyAboutWriteError
import com.github.bric3.excalidraw.support.ExcalidrawColorScheme
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.adviseNotNull
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import javax.swing.JComponent
import javax.swing.JPanel


class ExcalidrawEditor(
    private val project: Project,
    private val file: VirtualFile
) : FileEditor,
    EditorColorsListener,
    DumbAware {

    private val logger = thisLogger()

    private val lifetimeDef = LifetimeDefinition()
    private val lifetime = lifetimeDef.lifetime
    private val userDataHolder = UserDataHolderBase()

    override fun getFile() = file

    lateinit var viewController: ExcalidrawWebViewController
    private val jcefUnsupported by lazy { JCEFUnsupportedViewPanel() }
    private val actionPanel = ExcalidrawActionPanel()
    private val toolbarAndWebView: JPanel

    init {
        //subscribe to changes of the theme
        val settingsConnection = ApplicationManager.getApplication().messageBus.connect(this)
        settingsConnection.subscribe(EditorColorsManager.TOPIC, this)
        // TODO listen to settings change, something like: settingsConnection.subscribe(ExcalidrawSettingsChangedListener.TOPIC, this)

        initViewIfSupported().also {
            toolbarAndWebView = object : JPanel(BorderLayout()) {
                init {
                    when {
                        this@ExcalidrawEditor::viewController.isInitialized -> {
                            actionPanel.setTargetComponent(viewController.component)
                            add(actionPanel, BorderLayout.NORTH)
                            add(viewController.component, BorderLayout.CENTER)
                        }
                        else -> add(jcefUnsupported, BorderLayout.CENTER)
                    }
                }
            }
        }
    }

    private fun uiThemeFromConfig(): ExcalidrawColorScheme = when {
        UIUtil.isUnderDarcula() -> ExcalidrawColorScheme.DARK
        else -> ExcalidrawColorScheme.LIGHT
    }


    private fun initViewIfSupported() {
        if (JBCefApp.isSupported()) {
            viewController = ExcalidrawWebViewController(lifetime, uiThemeFromConfig().key)
            Disposer.register(this, viewController.jcefPanel)
        } else {
            Notifications.Bus.notify(
                Notification(
                    "Plugin Error",
                    "Excalidraw not available",
                    "CEF is not available on this JVM, use Jetbrains runtime",
                    NotificationType.ERROR,
                    null
                )
            )
            return
        }

        viewController.initialized().then {
            if (file.name.endsWith("excalidraw") || file.name.endsWith("json")) {
                file.inputStream.bufferedReader(UTF_8).use {
                    val jsonPayload = it.readText()
                    viewController.loadJsonPayload(jsonPayload)
                }
            }

            if (file.name.endsWith("svg") || file.name.endsWith("png")) {
                viewController.loadFromFile(file)

            }
            viewController.toggleReadOnly(file.isWritable.not())
        }

        // https://github.com/JetBrains/rd/blob/211/rd-kt/rd-core/src/commonMain/kotlin/com/jetbrains/rd/util/reactive/Interfaces.kt#L17
        viewController.excalidrawPayload.adviseNotNull(lifetime) { content ->
            logger.debug("content to save to $file")
            if (!file.isWritable) {
                return@adviseNotNull
            }
            val (type, b) = when {
                file.name.endsWith(".svg") -> {
                    TODO("Continuous saving to SVG is not yet supported")
//                    view.saveAsSvg().then{ data: String ->
//                        file.setBinaryContent(data.toByteArray(charset("utf-8"))
//                    }
                }
                file.name.endsWith(".png") -> {
                    TODO("Continuous saving to PNG is not yet supported")
//                    view.saveAsPng().then { data: ByteArray ->
//                        file.setBinaryContent(data)
//                    }
                }
                else -> {
                    Pair(ExcalidrawImageType.EXCALIDRAW,
                         content.toByteArray(UTF_8))
                }
            }
            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().runWriteAction {
                    try {
                        file.getOutputStream(file).use { stream ->
                            with(stream) {
                                write(b)
                            }
                        }
                    } catch (e: IOException) {
                        notifyAboutWriteError(type, file, e)
                    } catch (e: IllegalArgumentException) {
                        notifyAboutWriteError(type, file, e)
                    }
                }
            }
        }
    }

    @Override
    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        if (this::viewController.isInitialized) {
            viewController.changeTheme(uiThemeFromConfig().key)
        }
    }

    override fun getComponent(): JComponent = toolbarAndWebView

    override fun getPreferredFocusedComponent() = toolbarAndWebView

    override fun getName() = "Excalidraw"

    override fun setState(state: FileEditorState) {
    }

    override fun isModified(): Boolean {
        return false
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun getCurrentLocation(): FileEditorLocation? {
        return null
    }

    override fun dispose() {
        lifetimeDef.terminate(true)
    }

    override fun <T : Any?> getUserData(key: Key<T>): T? {
        return userDataHolder.getUserData(key)
    }

    override fun <T : Any?> putUserData(key: Key<T>, value: T?) {
        userDataHolder.putUserData(key, value)
    }
}
