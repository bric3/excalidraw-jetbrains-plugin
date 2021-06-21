package com.github.bric3.excalidraw.editor

import com.github.bric3.excalidraw.support.ExcalidrawColorScheme
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.lifetime.LifetimeDefinition
import org.freedesktop.dbus.bin.DBusDaemon.saveFile
import java.beans.PropertyChangeListener
import java.io.BufferedReader
import javax.swing.JComponent


class ExcalidrawEditor(
    private val project: Project,
    private val file: VirtualFile
) : FileEditor,
    EditorColorsListener,
    DumbAware {

    private val lifetimeDef = LifetimeDefinition()
    private val lifetime = lifetimeDef.lifetime
    private val userDataHolder = UserDataHolderBase()

    override fun getFile() = file

    private var view: ExcalidrawWebView

    private var isInvalid = false

    init {
        //subscribe to changes of the theme
        val settingsConnection = ApplicationManager.getApplication().messageBus.connect(this)
        settingsConnection.subscribe(EditorColorsManager.TOPIC, this)
        // TODO listen to settings change, something like: settingsConnection.subscribe(ExcalidrawSettingsChangedListener.TOPIC, this)

        view = ExcalidrawWebView(lifetime, uiThemeFromConfig().key)
        initView()
    }

    private fun uiThemeFromConfig(): ExcalidrawColorScheme = when {
        UIUtil.isUnderDarcula() -> ExcalidrawColorScheme.DARK
        else -> ExcalidrawColorScheme.LIGHT
    }


    private fun initView() {
        view.initialized().then { 
            if (file.name.endsWith("excalidraw") || file.name.endsWith("json")) {
                val jsonPayload = BufferedReader(file.inputStream.reader()).readText()
                view.loadJsonPayload(jsonPayload)
                view.toggleReadOnly(file.isWritable.not())
            }

            if (file.name.endsWith("svg")) {
                val builder = DialogBuilder().title("SVG edition not supported yet")
                builder.addOkAction()
                builder.show()

                isInvalid = true
//                val content:String = BufferedReader(file.inputStream.reader()).readText();
//                val json:String = ExcalidrawUtil.extractScene(content);
//                view.loadJsonPayload(json);
            }
        }

        // https://github.com/JetBrains/rd/blob/211/rd-kt/rd-core/src/commonMain/kotlin/com/jetbrains/rd/util/reactive/Interfaces.kt#L17
        view.excalidrawPayload.advise(lifetime) { content ->
            if (content !== null) {
                when {
                    file.name.endsWith(".svg") -> {
                        // ignore the xml payload and ask for an exported svg
//                        view.exportSvg().then{ data: String ->
//                            saveFile(data.toByteArray(charset("utf-8")))
//                        }
                    }
                    file.name.endsWith(".png") -> {
                        //ignore the xml payload and ask for an exported svg
//                        view.exportPng().then { data: ByteArray ->
//                            saveFile(data)
//                        }
                    }
                    else -> {
                        saveFile(content, file.canonicalPath)
                    }
                }
            }
        }

    }

    @Override
    override fun globalSchemeChange(scheme: EditorColorsScheme?) {
        view.changeTheme(uiThemeFromConfig().key)
    }

    override fun getComponent(): JComponent {
        return view.component
    }

    override fun getPreferredFocusedComponent(): JComponent {
        return view.component
    }

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