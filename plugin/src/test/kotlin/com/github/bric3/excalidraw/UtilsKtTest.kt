package com.github.bric3.excalidraw

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

internal class UtilsKtTest {
    @Test
    internal fun `handles null for implicit check cast`() {
        val anActionEvent = AnActionEvent(
            null,
            DataContext {
                when (it) {
                    CommonDataKeys.PROJECT.name -> mock<Project>()
                    else -> null
                }
            },
            "Place",
            Presentation(),
            mock(),
            0
        )

        assertThat(anActionEvent.findEditor()).isNull()
    }
}