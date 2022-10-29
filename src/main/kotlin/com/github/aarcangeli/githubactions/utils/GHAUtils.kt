package com.github.aarcangeli.githubactions.utils

import com.intellij.openapi.vfs.VirtualFile

class GHAUtils {
  companion object {
    /**
     * A workflow path is a file that is named `*.yml` or `*.yaml` and is located in the `.github/workflows` directory.
     * ref: https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#about-yaml-syntax-for-workflows
     */
    fun isWorkflowPath(file: VirtualFile): Boolean {
      if (file.isDirectory || file.extension != "yml" && file.extension != "yaml") {
        return false
      }
      val workflows = file.parent
      if (workflows == null || workflows.name != "workflows") {
        return false
      }
      val github = workflows.parent
      if (github == null || github.name != ".github") {
        return false
      }

      // skip if the root directory is not a git repository
      return true
    }
  }
}
