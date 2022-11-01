package com.github.aarcangeli.githubactions.utils

import com.github.aarcangeli.githubactions.GHABundle
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLScalar

class ReplaceWithTrimmed(val uses: YAMLScalar) : IntentionAction {
  override fun startInWriteAction(): Boolean {
    return true
  }

  override fun getText(): String {
    return GHABundle.message("highlighting.replace.with.trimmed")
  }

  override fun getFamilyName(): String {
    return text
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    uses.updateText(uses.textValue.trim())
  }
}
