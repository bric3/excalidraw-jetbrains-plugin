package com.github.bric3.excalidraw

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.project.Project
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UtilsKtTest {
    @Test
    internal fun `handles null for implicit check cast`() {
        val anActionEvent = AnActionEvent(
            null,
            {
                when (it) {
                    CommonDataKeys.PROJECT.name -> mockk<Project>()
                    else -> null
                }
            },
            "Place",
            Presentation(),
            mockk(),
            0
        )

        assertThat(anActionEvent.findEditor()).isNull()
    }
}