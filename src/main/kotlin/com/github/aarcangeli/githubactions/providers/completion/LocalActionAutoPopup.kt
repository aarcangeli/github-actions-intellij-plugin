package com.github.aarcangeli.githubactions.providers.completion

import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLScalar

class LocalActionAutoPopup : TypedHandlerDelegate() {
  override fun checkAutoPopup(charTyped: Char, project: Project, editor: Editor, file: PsiFile): Result {
    if (charTyped == '/' && file is YAMLFile && GHAUtils.isWorkflowPath(file.virtualFile)) {
      file.findElementAt(editor.caretModel.offset - 1)?.parentOfType<YAMLScalar>()?.let {
        if (GHAUtils.getStepFromUses(it) != null) {
          AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        }
      }
    }
    return Result.CONTINUE
  }
}
