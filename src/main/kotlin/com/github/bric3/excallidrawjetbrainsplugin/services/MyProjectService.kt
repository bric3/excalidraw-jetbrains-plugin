package com.github.bric3.excallidrawjetbrainsplugin.services

import com.github.bric3.excallidrawjetbrainsplugin.MyBundle
import com.intellij.openapi.project.Project

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
