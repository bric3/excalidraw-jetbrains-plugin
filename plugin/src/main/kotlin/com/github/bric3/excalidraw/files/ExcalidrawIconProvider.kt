package com.github.bric3.excalidraw.files

import com.intellij.ide.IconProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import icons.ExcalidrawIcons
import javax.swing.Icon

/**
 * Identify excalidraw files.
 */
class ExcalidrawIconProvider : DumbAware, IconProvider() {
    override fun getIcon(element: PsiElement, flags: Int): Icon? {
        if (element is PsiFile) {
            if (ExcalidrawFiles.isExcalidrawFile(element.virtualFile)) {
                return ExcalidrawIcons.ExcalidrawFileIcon
            }
        }
        return null
    }
}
