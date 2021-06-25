package com.github.bric3.excalidraw.editor

import com.github.bric3.excalidraw.support.ExcalidrawColorScheme
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import com.jetbrains.rd.util.reactive.adviseNotNull
import java.beans.PropertyChangeListener
import java.io.BufferedReader
import javax.swing.JComponent


class ExcalidrawEditor(
    private val project: Project,
    private val file: VirtualFile
) : FileEditor,
    EditorColorsListener,
    DumbAware {

    private val logger = Logger.getInstance(ExcalidrawEditor::class.java)

    private val lifetimeDef = LifetimeDefinition()
    private val lifetime = lifetimeDef.lifetime
    private val userDataHolder = UserDataHolderBase()

    override fun getFile() = file

    private lateinit var view: ExcalidrawWebView
    private val jcefUnsupported by lazy { JCEFUnsupportedViewPanel() }

    private var isInvalid = false

    init {
        //subscribe to changes of the theme
        val settingsConnection = ApplicationManager.getApplication().messageBus.connect(this)
        settingsConnection.subscribe(EditorColorsManager.TOPIC, this)
        // TODO listen to settings change, something like: settingsConnection.subscribe(ExcalidrawSettingsChangedListener.TOPIC, this)

        if (JBCefApp.isSupported()) {
            view = ExcalidrawWebView(lifetime, uiThemeFromConfig().key)
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
        }

        initView()
    }

    private fun uiThemeFromConfig(): ExcalidrawColorScheme = when {
        UIUtil.isUnderDarcula() -> ExcalidrawColorScheme.DARK
        else -> ExcalidrawColorScheme.LIGHT
    }


    private fun initView() {
        if (!this::view.isInitialized) {
            return
        }
        view.initialized().then {
            if (file.name.endsWith("excalidraw") || file.name.endsWith("json")) {
                val jsonPayload = BufferedReader(file.inputStream.reader()).readText()
                view.loadJsonPayload(jsonPayload)
                view.toggleReadOnly(file.isWritable.not())
            }

            if (file.name.endsWith("svg")) {
                isInvalid = true
                TODO("Loading from SVG is not yet supported")
//                val content:String = BufferedReader(file.inputStream.reader()).readText();
//                val json:String = ExcalidrawUtil.extractScene(content);
//                view.loadJsonPayload(json);
            }
        }

        // https://github.com/JetBrains/rd/blob/211/rd-kt/rd-core/src/commonMain/kotlin/com/jetbrains/rd/util/reactive/Interfaces.kt#L17
        view.excalidrawPayload.adviseNotNull(lifetime) { content ->
            logger.debug("content to save")
            when {
                file.name.endsWith(".svg") -> {
                    TODO("Saving to SVG is not yet supported")
//                    view.saveAsSvg().then{ data: String ->
//                        file.setBinaryContent(data.toByteArray(charset("utf-8"))
//                    }
                }
                file.name.endsWith(".png") -> {
                    TODO("Saving to PNG is not yet supported")
//                    view.saveAsPng().then { data: ByteArray ->
//                        file.setBinaryContent(data)
//                    }
                }
                else -> {
                    ApplicationManager.getApplication().invokeLater {
                        ApplicationManager.getApplication().runWriteAction {
                            VfsUtil.saveText(file, content)
                        }
                    }
                }
            }
        }
    }

    @Override
    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        if (this::view.isInitialized) {
            view.changeTheme(uiThemeFromConfig().key)
        }
    }

    override fun getComponent(): JComponent {
        return when {
            this::view.isInitialized -> view.component
            else -> jcefUnsupported
        }
    }

    override fun getPreferredFocusedComponent() = component

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