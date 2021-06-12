package com.github.bric3.excalidrawjetbrainsplugin.services

import com.github.bric3.excalidrawjetbrainsplugin.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}