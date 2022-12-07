package com.github.bric3.excalidraw.editor

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class ExcalidrawActionPanel : JPanel(BorderLayout()) {
    private val leftToolbar = run {
        val group = ActionManager.getInstance().getAction("excalidraw.ToolbarActionGroup")
        checkNotNull(group)
        check(group is ActionGroup)
        ActionManager.getInstance().createActionToolbar("excalidraw.ExcalidrawActionPanel", group, true)
    }

    init {
        leftToolbar.component.border = null
        add(leftToolbar.component, BorderLayout.CENTER)
    }

    fun setTargetComponent(component: JComponent) {
        leftToolbar.targetComponent = component
    }
}