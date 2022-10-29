package com.github.aarcangeli.githubactions.services

import com.intellij.openapi.project.Project
import com.github.aarcangeli.githubactions.MyBundle

class MyProjectService(project: Project) {

    init {
        println(MyBundle.message("projectService", project.name))
    }
}
