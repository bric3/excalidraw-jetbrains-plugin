package com.github.bric3.excalidraw.editor

import com.github.bric3.excalidraw.SaveOptions
import com.github.bric3.excalidraw.debuggingLogWithThread
import com.github.bric3.excalidraw.files.EXCALIDRAW_IMAGE_TYPE
import com.github.bric3.excalidraw.files.ExcalidrawImageType
import com.github.bric3.excalidraw.support.ExcalidrawColorScheme
import com.github.bric3.excalidraw.writePayloadToDocument
import com.github.bric3.excalidraw.writePayloadToFile
import com.intellij.AppTopics
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.findDocument
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.nio.charset.StandardCharsets.UTF_8
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.time.Duration.Companion.milliseconds


@OptIn(FlowPreview::class)
class ExcalidrawEditor(
    private val project: Project,
    private val file: VirtualFile
) : UserDataHolderBase(),
    FileEditor,
    EditorColorsListener,
    DumbAware {

    private var isDisposed: Boolean = false
    private val logger = thisLogger()

    override fun getFile() = file

    lateinit var viewController: ExcalidrawWebViewController
    private val jcefUnsupported by lazy { JCEFUnsupportedViewPanel() }
    private val actionPanel = ExcalidrawActionPanel()
    private val toolbarAndWebView: JPanel
    private val propertyChangeSupport = PropertyChangeSupport(this)

    @Volatile
    private var modified = false

    private val coroutineScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + CoroutineName("${this::class.java.simpleName}:${file.name}"))

    init {
        // subscribe to changes of the theme
        val busConnection = ApplicationManager.getApplication().messageBus.connect(this)
        with(busConnection) {
            subscribe(EditorColorsManager.TOPIC, this@ExcalidrawEditor)
            subscribe(EditorColorsManager.TOPIC, this@ExcalidrawEditor)
            subscribe(AppTopics.FILE_DOCUMENT_SYNC, object : FileDocumentManagerListener {
                override fun beforeAllDocumentsSaving() {
                    // This is the manual or auto save action of IntelliJ
                    debuggingLogWithThread(logger) { "ExcalidrawEditor::beforeAllDocumentsSaving" }
                    saveEditor()
                    // AWT-EventQueue-0 : 26 : ExcalidrawEditor::beforeAllDocumentsSaving
                    // AWT-EventQueue-0 : 26 : ExcalidrawEditor::saveEditor
                    // AWT-EventQueue-0 : 26 : ExcalidrawWebViewController::saveAs
                    // AWT-AppKit : 23 : CefMessageRouterHandlerAdapter::onQuery
                    // AWT-AppKit : 23 : ExcalidrawEditor::saveEditor.then write promise
                    // AWT-AppKit : 23 : ExcalidrawEditor::saveEditor.then write done promise
                    // AWT-EventQueue-0 : 26 : asyncWrite
                }
            })

            // // Before file close
            // busConnection.subscribe(
            //     FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
            //     object : FileEditorManagerListener.Before {
            //         override fun beforeFileClosed(source: FileEditorManager, file: VirtualFile) {
            //             logWithThread("ExcalidrawEditor::beforeFileClosed ${file.name}")
            //         }
            //     })

            subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    events
                        .filterIsInstance<VFilePropertyChangeEvent>()
                        .filter { it.file == file && it.propertyName == "writable" }
                        .forEach {
                            viewController.toggleReadOnly(file.isWritable.not())
                            logger.debug("${it.javaClass.simpleName}: ${it.file.name} writable : ${it.file.isWritable}")
                        }
                }
            })
        }

        // TODO listen to settings change, something like: busConnection.subscribe(ExcalidrawSettingsChangedListener.TOPIC, this)

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
        if (ExcalidrawWebViewController.isSupported) {
            viewController = ExcalidrawWebViewController(
                this,
                file.name,
                uiThemeFromConfig().key
            )
            launchModifiedStatusJob()
            launchSaveJob()
            loadFileWhenReady()
        } else {
            Notifications.Bus.notify(
                Notification(
                    "excalidraw.error",
                    "Excalidraw not available",
                    "CEF is not available on this JVM, use Jetbrains runtime",
                    NotificationType.ERROR,
                )
            )
            return
        }
    }

    private fun loadFileWhenReady() {
        coroutineScope.launch(Dispatchers.IO) {
            viewController.whenReady.collectLatest {
                when {
                    file.name.endsWith("excalidraw") || file.name.endsWith("json") -> {
                        val document = readAction(file::findDocument)

                        if (document != null) document.let {
                            viewController.loadJsonPayload(it.text)
                        } else {
                            file.inputStream.bufferedReader(UTF_8).use {
                                val jsonPayload = it.readText()
                                if (jsonPayload.isNotEmpty()) {
                                    viewController.loadJsonPayload(jsonPayload)
                                }
                            }
                        }
                    }

                    file.name.endsWith("svg") || file.name.endsWith("png") -> {
                        viewController.loadFromImageFile(file)
                    }

                    else -> logger.warn("Unsupported file type ${file.name}")
                }

                viewController.toggleReadOnly(file.isWritable.not())
            }
        }
    }

    private suspend fun toggleModifiedStatus(newModificationStatus: Boolean) {
        val oldModificationStatus = modified
        if (oldModificationStatus == newModificationStatus) {
            return
        }
        modified = newModificationStatus

        withContext(Dispatchers.EDT) {
            propertyChangeSupport.firePropertyChange(
                FileEditor.PROP_MODIFIED,
                oldModificationStatus,
                newModificationStatus
            )
        }
    }

    // TODO add scene version in file user data
    // This will trigger the saveJob
    private fun saveEditor() = coroutineScope.launch {
        val saveOptions = getUserData(SaveOptions.SAVE_OPTIONS_KEY) ?: SaveOptions()
        val type = file.getUserData(EXCALIDRAW_IMAGE_TYPE)
            ?: throw IllegalStateException("Excalidraw should have been identified")

        viewController.triggerSnapshot(type, saveOptions)
    }

    private fun launchSaveJob() = coroutineScope.launch {
        viewController.payload
            .debounce(250.milliseconds)
            .filterNotNull()
            .collectLatest { payload ->
                if (isDisposed) {
                    debuggingLogWithThread(logger) { "Saving aborted: disposed" }
                    return@collectLatest
                }

                debuggingLogWithThread(logger) { "Saving started" }
                if (!file.isWritable) {
                    debuggingLogWithThread(logger) { "Bailing out save, file non writable" }
                    return@collectLatest
                }
                val type = file.getUserData(EXCALIDRAW_IMAGE_TYPE)
                    ?: throw IllegalStateException("Excalidraw should have been identified")

                when (type) {
                    ExcalidrawImageType.EXCALIDRAW, ExcalidrawImageType.SVG -> {
                        writePayloadToDocument(
                            { file },
                            payload
                        )
                    }

                    ExcalidrawImageType.PNG -> {
                        val bytes = Base64.getDecoder()
                            .decode(payload.substringAfter("data:image/png;base64,"))

                        writePayloadToFile(
                            { file },
                            type,
                            bytes
                        )
                    }
                }

                debuggingLogWithThread(logger) { "File ${file.name} saved" }
                toggleModifiedStatus(false)
            }
    }

    private fun launchModifiedStatusJob() = coroutineScope.launch {
        viewController.payload
            .filterNotNull()
            .collectLatest {
                debuggingLogWithThread(logger) { "content to save to $file" }
                if (!file.isWritable) {
                    return@collectLatest
                }
                toggleModifiedStatus(true)
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
        return modified
    }

    override fun isValid(): Boolean {
        return true
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    override fun deselectNotify() {
        // if closing the editor it's preceded by
        // com.intellij.openapi.fileEditor.FileEditorManagerListener.Before.beforeFileClosed
        // changing (and current editor gets deselected) editor triggers
        debuggingLogWithThread(logger) { "ExcalidrawEditor::deselectNotify" }
        saveEditor()

        // deselectNotify
        // AWT-EventQueue-0 : 26 : ExcalidrawEditor::saveEditor
        // AWT-EventQueue-0 : 26 : ExcalidrawWebViewController::saveAs
        // AWT-AppKit : 23 : CefMessageRouterHandlerAdapter::onQuery
        // AWT-AppKit : 23 : ExcalidrawEditor::saveEditor.then write promise
        // AWT-AppKit : 23 : ExcalidrawEditor::saveEditor.then write done promise
        // AWT-EventQueue-0 : 26 : asyncWrite

        // AWT-EventQueue-0 : 26 : ExcalidrawEditor::beforeFileClosed random.excalidraw
        // AWT-EventQueue-0 : 26 : ExcalidrawEditor::deselectNotify
        // AWT-EventQueue-0 : 26 : ExcalidrawEditor::saveEditor
        // AWT-EventQueue-0 : 26 : ExcalidrawWebViewController::saveAs
        // AWT-EventQueue-0 : 26 : LoadableJCEFHtmlPanel::dispose
        // AWT-EventQueue-0 : 26 : ExcalidrawEditor::dispose
    }

    override fun getCurrentLocation(): FileEditorLocation? {
        return null
    }

    override fun dispose() {
        isDisposed = true

        // TODO proper cancel ?
        coroutineScope.cancel()
    }
}
