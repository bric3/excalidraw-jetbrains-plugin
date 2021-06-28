package com.github.bric3.excalidraw.files

import com.github.bric3.excalidraw.ExcalidrawJson
import com.intellij.json.JsonParserDefinition
import com.intellij.json.psi.impl.JsonFileImpl
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IFileElementType

class ExcalidrawJsonParserDefinition : JsonParserDefinition() {
    companion object {
        private val EXCALIDRAW_JSON_FILE = IFileElementType(ExcalidrawJson)
    }

    override fun createFile(fileViewProvider: FileViewProvider?): PsiFile? {
        return JsonFileImpl(fileViewProvider, ExcalidrawJson)
    }

    override fun getFileNodeType(): IFileElementType {
        return EXCALIDRAW_JSON_FILE
    }
}