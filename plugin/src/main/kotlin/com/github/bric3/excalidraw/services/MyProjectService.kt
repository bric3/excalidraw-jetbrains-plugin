package com.github.bric3.excalidraw.services

import com.github.bric3.excalidraw.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}