package com.github.aarcangeli.githubactions.highlighting

import com.github.aarcangeli.githubactions.GHABundle
import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.ActionStatus
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.github.aarcangeli.githubactions.domain.StepElement
import com.github.aarcangeli.githubactions.references.InputPropertyReference
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.github.aarcangeli.githubactions.utils.GHAUtils.isWorkflowPath
import com.github.aarcangeli.githubactions.utils.ReplaceWithTrimmed
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.HighlightVisitor
import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.FileContentUtilCore
import org.jetbrains.yaml.psi.YAMLKeyValue
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

  override fun visitKeyValue(keyValue: YAMLKeyValue) {
    for (reference in keyValue.references) {
      if (reference is InputPropertyReference && !reference.isSoft) {
        val actionDescription = reference.findActionDescription() ?: continue
        if (reference.resolve() == null) {
          val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
            .range(keyValue.key?.textRange ?: continue)
            .descriptionAndTooltip(
              GHABundle.message(
                "highlighting.uses.action.input.not.found",
                keyValue.keyText,
                actionDescription.toString()
              )
            )
            .create()
          holder.add(info)
        }
      }
    }
  }

  override fun visitMapping(mapping: YAMLMapping) {
    StepElement.fromYaml(mapping)?.let { step ->
      checkUses(step.getUses() ?: return)

      // check deprecated inputs
      for (input in step.getWithInputs()) {
        val reference = input.references.find { it is InputPropertyReference } ?: continue
        val target = reference.resolve() as? YAMLKeyValue ?: continue
        val inputMapping = target.value as? YAMLMapping ?: continue

        val deprecationMessage = inputMapping.getKeyValueByKey("deprecationMessage")?.valueText
        if (deprecationMessage != null) {
          val info = HighlightInfo.newHighlightInfo(HighlightInfoType.DEPRECATED)
            .range(input.key?.textRange ?: continue)
            .descriptionAndTooltip(GHABundle.message("highlighting.deprecated.input.used", input.keyText, deprecationMessage))
            .create()
          holder.add(info)
        }
      }
    }
  }

  /**
   * Validates the content of the `uses` field.
   *
   * Eg:
   * ```yaml
   *   uses: "<owner>/<repo>@<ref>"
   * ```
   */
  private fun checkUses(uses: YAMLScalar) {
    val containingFile = uses.containingFile ?: return
    val content = uses.textValue

    // Ignore validations for empty string (schema validation will catch it)
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

    val actionDescription = ActionDescription.fromString(content)

    if (!actionDescription.isValid(ignoreRef = true, ignoreDockerTag = true)) {
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(uses)
        .descriptionAndTooltip(GHABundle.message("highlighting.uses.invalid"))
        .create()
      holder.add(info)
      return
    }

    // validate version name
    if (actionDescription.ref != null && !actionDescription.isRefValid()) {
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
    if (actionDescription.dockerTag != null && !actionDescription.isDockerTagValid()) {
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
        .range(uses, GHAUtils.findRange(uses, TextRange(content.length - actionDescription.dockerTag.length, content.length)))
        .descriptionAndTooltip(GHABundle.message("highlighting.uses.invalid.tag"))
        .create()
      holder.add(info)
      return
    }

    if (actionDescription.isStandardAction()) {
      if (actionDescription.ref == null) {
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
          .range(uses)
          .descriptionAndTooltip(GHABundle.message("highlighting.uses.missing-ref"))
          .create()
        holder.add(info)
        return
      }

      val actionStatus = service<RemoteActionManager>().getActionStatus(actionDescription, containingFile)
      if (actionStatus == ActionStatus.FAILED) {
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WARNING)
          .range(uses)
          .descriptionAndTooltip(GHABundle.message("highlighting.uses.action.not.fetched", actionDescription.getFullName()))
          .create()
        QuickFixAction.registerQuickFixAction(info, DownloadAgain(uses, GHABundle.message("highlighting.uses.action.download.again")))
        holder.add(info)
        return
      }
      if (actionStatus == ActionStatus.ACTION_NOT_FOUND) {
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
          .range(uses)
          .descriptionAndTooltip(GHABundle.message("highlighting.uses.action.not.found", actionDescription.getFullName()))
          .create()
        holder.add(info)
        return
      }
      if (actionStatus == ActionStatus.ACTION_REVISION_NOT_FOUND) {
        val index = content.indexOf('@')
        assert(index != -1)
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.WRONG_REF)
          .range(uses, GHAUtils.findRange(uses, TextRange(index + 1, index + content.length)))
          .descriptionAndTooltip(
            GHABundle.message(
              "highlighting.uses.revision.not.found",
              actionDescription.ref,
              actionDescription.getFullName()
            )
          )
          .create()
        holder.add(info)
      }

      // refresh action
      val info = HighlightInfo.newHighlightInfo(HighlightInfoType.INFORMATION)
        .range(uses)
        .descriptionAndTooltip("") // hack: empty string otherwise the info is now shown
        .create()
      QuickFixAction.registerQuickFixAction(info, DownloadAgain(uses, GHABundle.message("highlighting.uses.refresh")))
      holder.add(info)
      return
    }
  }
}

private class DownloadAgain(private val uses: YAMLScalar, private val text: String) : IntentionAction {
  override fun startInWriteAction(): Boolean {
    return false
  }

  override fun getText(): String {
    return text
  }

  override fun getFamilyName(): String {
    return text
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    return true
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    service<RemoteActionManager>().retryAllFailedActions()
    service<RemoteActionManager>().refreshAction(uses.textValue, file)
    FileContentUtilCore.reparseFiles(file?.virtualFile ?: return)
  }
}
