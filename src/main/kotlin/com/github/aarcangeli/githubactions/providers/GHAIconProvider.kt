package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.icons.AllIcons
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

class GHAIconProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (GHAUtils.isWorkflowPath(file)) {
      return AllIcons.Vcs.Vendors.Github
    }
    return null
  }
}
