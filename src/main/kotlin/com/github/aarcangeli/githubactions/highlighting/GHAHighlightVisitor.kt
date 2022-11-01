package com.github.aarcangeli.githubactions.highlighting

import com.github.aarcangeli.githubactions.GHABundle
import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.domain.StepElement
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.github.aarcangeli.githubactions.utils.GHAUtils.isWorkflowPath
import com.github.aarcangeli.githubactions.utils.ReplaceWithTrimmed
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

class GHAHighlightVisitor : YamlPsiElementVisitor(), HighlightVisitor, DumbAware {
  private lateinit var holder: HighlightInfoHolder

  override fun clone(): HighlightVisitor {
    return GHAHighlightVisitor()
  }

  override fun suitableForFile(file: PsiFile): Boolean {
    return isWorkflowPath(file.virtualFile ?: return false)
  }

  override fun analyze(file: PsiFile, updateWholeFile: Boolean, holder: HighlightInfoHolder, action: Runnable): Boolean {
    this.holder = holder
    action.run()
    return true
  }

  override fun visit(element: PsiElement) {
    element.accept(this)
  }

  override fun visitMapping(mapping: YAMLMapping) {
    StepElement.fromYaml(mapping)?.let { step ->
      checkUses(step.getUses() ?: return)
    }
  }

  private fun checkUses(uses: YAMLScalar) {
    val content = uses.textValue

    // schema already checks for empty string
    if (content.isEmpty()) return

    // GitHub doesn't support untrimmed the values
    if (content.trim() != content) {
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(uses)
        .descriptionAndTooltip(GHABundle.message("highlighting.uses.trim"))
        .create()
      QuickFixAction.registerQuickFixAction(info, ReplaceWithTrimmed(uses))
      holder.add(info)
      return
    }

    val action = ActionDescription.fromString(content)

    if (!action.isValid(ignoreRef = true, ignoreDockerTag = true)) {
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(uses)
        .descriptionAndTooltip(GHABundle.message("highlighting.uses.invalid"))
        .create()
      holder.add(info)
      return
    }

    // validate version name
    if (action.ref != null && !action.isRefValid()) {
      val index = content.indexOf('@')
      if (index != -1) {
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(uses, GHAUtils.findRange(uses, TextRange(index + 1, index + content.length)))
          .descriptionAndTooltip(GHABundle.message("highlighting.uses.invalid.version"))
          .create()
        holder.add(info)
        return
      }
    }

    // validate docker tag
    if (action.dockerTag != null && !action.isDockerTagValid()) {
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(uses, GHAUtils.findRange(uses, TextRange(content.length - action.dockerTag.length, content.length)))
        .descriptionAndTooltip(GHABundle.message("highlighting.uses.invalid.tag"))
        .create()
      holder.add(info)
      return
    }

    if (action.isStandardAction()) {
      if (action.ref == null) {
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(uses)
          .descriptionAndTooltip(GHABundle.message("highlighting.uses.missing-ref"))
          .create()
        holder.add(info)
      }
    }
  }
}
