package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.impl.http.RemoteFileEditorActionProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile

/**
 * Provides an action to open GitHub repository when the action file ("https://raw.githubusercontent.com/.../action.yml") is opened.
 */
class GHAEditorActionProvider : RemoteFileEditorActionProvider(), DumbAware {
  override fun createToolbarActions(project: Project, file: HttpVirtualFile): Array<AnAction> {
    ActionDescription.fromUrl(file.url)?.let {
      it.toUrl()?.let { url ->
        return arrayOf(GHAAction(url))
      }
    }
    return emptyArray()
  }
}

private class GHAAction(val url: String) :
  AnAction("Open Repository", "Open repository on GitHub", AllIcons.Vcs.Vendors.Github) {
  override fun actionPerformed(e: AnActionEvent) {
    BrowserUtil.browse(url)
  }
}
