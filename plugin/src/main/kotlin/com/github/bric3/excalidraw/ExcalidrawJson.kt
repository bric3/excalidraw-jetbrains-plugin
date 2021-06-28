package com.github.bric3.excalidraw

import com.intellij.json.JsonLanguage
import com.intellij.lang.Language

/**
 * Allows to register Excalidraw as a language
 */
object ExcalidrawJson : Language(JsonLanguage.INSTANCE, "Excalidraw", "application/vnd.excalidraw+json")