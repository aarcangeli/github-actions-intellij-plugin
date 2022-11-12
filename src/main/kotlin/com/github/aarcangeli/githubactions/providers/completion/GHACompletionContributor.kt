package com.github.aarcangeli.githubactions.providers.completion

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.github.aarcangeli.githubactions.domain.StepElement
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.*

class GHACompletionContributor : CompletionContributor() {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val step = findStep(parameters.position) ?: return

    val uses = ActionDescription.fromString(step.getUses()?.textValue ?: return)

    if (uses.isStandardAction()) {
      val actionFile = service<RemoteActionManager>().getActionFile(uses, parameters.originalFile) ?: return
      for (input in findInputs(actionFile)) {
        val inputDescription: YAMLMapping = input.value
        var builder = LookupElementBuilder.create(input.key)

        builder = builder.withPsiElement(inputDescription)

        // documentation
        inputDescription.getKeyValueByKey("description")?.value.let { it as? YAMLScalar }?.textValue.let {
          builder = builder.withTypeText(it)
        }

        // icon
        builder = builder.withIcon(AllIcons.Vcs.Vendors.Github)

        // insertion handler
        builder = builder.withInsertHandler(InsertHandler { context, item ->
          val editor = context.editor
          val docChars = context.document.charsSequence
          val caretModel = editor.caretModel
          if (caretModel.offset >= docChars.length) {
            return@InsertHandler
          }

          if (caretModel.offset < docChars.length && docChars[caretModel.offset] == '"') {
            caretModel.moveToOffset(caretModel.offset + 1)
          }

          // add ": " after the input name
          if (caretModel.offset < docChars.length) {
            if (docChars[caretModel.offset] == ':') {
              caretModel.moveToOffset(caretModel.offset + 1)
            }
            else {
              EditorModificationUtil.insertStringAtCaret(editor, ":", false, true, 1)
            }
          }
          if (caretModel.offset < docChars.length) {
            if (docChars[caretModel.offset] == ' ') {
              caretModel.moveToOffset(caretModel.offset + 1)
            }
            else {
              EditorModificationUtil.insertStringAtCaret(editor, " ", false, true, 1)
            }
          }

          // Auto popup
          AutoPopupController.getInstance(context.project).autoPopupMemberLookup(context.editor, null)
        })

        result.addElement(builder)
      }
    }
  }

  private fun findInputs(actionFile: YAMLFile): Map<String, YAMLMapping> {
    val result = mutableMapOf<String, YAMLMapping>()
    for (document in actionFile.documents) {
      val root = document.topLevelValue as? YAMLMapping ?: continue
      val jobs = root.getKeyValueByKey("inputs")?.value as? YAMLMapping ?: continue
      for (job in jobs.keyValues) {
        if (job.keyText.isNotEmpty()) {
          result[job.keyText] = job.value as? YAMLMapping ?: continue
        }
      }
    }
    return result
  }

  private fun findStep(element: PsiElement): StepElement? {
    var current: PsiElement? = element
    while (current != null && current !is PsiFile) {
      if (current is YAMLKeyValue) {
        return getContainingStep(current)
      }
      if (current is YAMLScalar) {
        // this is possible when the value is not present
        // es:
        //   - uses: actions/checkout@v2
        //     with:
        //       my-incomplete-input
        return getContainingStep(current)
      }
      current = current.parent
    }
    return null
  }

  private fun getContainingStep(element: YAMLPsiElement): StepElement? {
    val withElement = element.parent as? YAMLMapping ?: return null
    val with = withElement.parent as? YAMLKeyValue ?: return null
    if (with.keyText != "with") return null
    val step = with.parent as? YAMLMapping ?: return null
    return StepElement.fromYaml(step)
  }
}
