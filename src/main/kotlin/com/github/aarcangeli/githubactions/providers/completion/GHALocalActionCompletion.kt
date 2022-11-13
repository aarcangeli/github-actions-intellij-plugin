package com.github.aarcangeli.githubactions.providers.completion

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionInitializationContext
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.AutoCompletionPolicy
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLScalar

class GHALocalActionCompletion : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val uses = parameters.position.parentOfType<YAMLScalar>() ?: return
    GHAUtils.getStepFromUses(uses) ?: return

    val actionDescription = ActionDescription.fromString(uses.textValue)
    if (!actionDescription.isLocalPath()) {
      return
    }

    // Get the left part of the path
    val left = actionDescription.path!!.split(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED).first()
    assert(left.startsWith("./")) { "Local path should start with './'" }

    val gitRoot = GHAUtils.getGitRoot(parameters.originalFile.virtualFile) ?: return

    // finds all action files with index
    processAllActions(parameters.position.project) {
      val relativePath = VfsUtil.getRelativePath(it.parent, gitRoot) ?: return@processAllActions
      result.addElement(LookupElementBuilder.create("./$relativePath").withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE))
    }

    // finds all action files with index
    val newPrefix = left.split("/").last()
    val innerResult = result.withPrefixMatcher(newPrefix)
    processRelativeFiles(gitRoot, left) {
      var builder = LookupElementBuilder.create(it.name)

      // if it is not a valid action directory, add a slash to the end
      if (it.findChild("action.yml") == null && it.findChild("action.yml") == null) {
        builder = builder.withInsertHandler { context, _ ->
          val offset = context.editor.caretModel.offset
          if (context.document.charsSequence[offset] != '/') {
            context.document.insertString(offset, "/")
          }
          context.editor.caretModel.moveToOffset(offset + 1)
          AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
        }
      }

      innerResult.addElement(builder.withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE))
    }
  }

  private fun processAllActions(project: Project, fn: (path: VirtualFile) -> Unit) {
    for (file in FilenameIndex.getVirtualFilesByName("action.yml", GlobalSearchScope.projectScope(project))) {
      fn(file)
    }
    for (file in FilenameIndex.getVirtualFilesByName("action.yaml", GlobalSearchScope.projectScope(project))) {
      fn(file)
    }
  }

  private fun processRelativeFiles(gitRoot: VirtualFile, left: String, fn: (path: VirtualFile) -> Unit) {
    assert(left.startsWith("./")) { "Local path should start with './'" }
    var relativePath = left.substring(2)

    // if the path is complete, remove last component
    if (!relativePath.endsWith("/")) {
      relativePath = relativePath.split("/").dropLast(1).joinToString("/")
    }

    val relativeDir = gitRoot.findFileByRelativePath(relativePath) ?: return
    for (child in relativeDir.children) {
      if (child.isDirectory) {
        fn(child)
      }
    }
  }
}
