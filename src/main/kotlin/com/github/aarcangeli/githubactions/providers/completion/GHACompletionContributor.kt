package com.github.aarcangeli.githubactions.providers.completion

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.github.aarcangeli.githubactions.domain.StepElement
import com.github.aarcangeli.githubactions.utils.GHAUtils.findInputs
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLPsiElement
import org.jetbrains.yaml.psi.YAMLScalar

class GHACompletionContributor : CompletionContributor(), DumbAware {
  override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
    val step = findStep(parameters.position) ?: return

    val uses = ActionDescription.fromString(step.getUses()?.textValue ?: return)
    val currentInputs = step.getWithInputs()?.map { it.keyText } ?: emptyList()

    val actionFile = service<RemoteActionManager>().getActionFile(uses, parameters.originalFile) ?: return
    for (input in findInputs(actionFile)) {
      // don't suggest already defined inputs
      if (input.key in currentInputs) continue

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

        // add default value
        inputDescription.getKeyValueByKey("default")?.value.let { it as? YAMLScalar }?.textValue?.let {
          if (!it.contains("\n")) {
            val startOffset = caretModel.offset
            val endOffset = EditorModificationUtil.insertStringAtCaret(editor, it, false, false, it.length)
            caretModel.currentCaret.moveToOffset(startOffset)
            caretModel.currentCaret.setSelection(startOffset, endOffset)
          }
        }
      })

      result.addElement(builder)
    }

    // don't show "args" and "with" suggestions for non docker actions
    if (uses.isStandardAction() || uses.isLocalPath()) {
      result.runRemainingContributors(parameters) { lookupElement ->
        if (lookupElement.lookupElement.lookupString !in listOf("args", "entrypoint")) {
          result.addElement(lookupElement.lookupElement)
        }
      }
    }
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
