package com.github.aarcangeli.githubactions.references

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * Represents a reference to an input property.
 *
 * ES:
 * ```yaml
 * - uses: actions/checkout@v2
 *   with:
 *     my-input: value
 * ```
 */
class InputPropertyReference(element: YAMLKeyValue) : PsiReferenceBase<YAMLKeyValue>(element) {
  override fun resolve(): PsiElement? {
    return ResolveCache.getInstance(element.project).resolveWithCaching(this, { _, _ -> resolveInner() }, false, false)
  }

  override fun isSoft(): Boolean {
    // If the action file is not resolved, the reference is soft (it doesn't show an error)
    if (findActionFile() == null) {
      return true
    }
    return false
  }

  private fun resolveInner(): PsiElement? {
    // Find the action file
    val actionFile: YAMLFile = findActionFile() ?: return null

    val inputName = element.keyText

    for (input in GHAUtils.findInputs(actionFile)) {
      if (input.key == inputName) {
        return input.value
      }
    }

    return null
  }

  private fun findActionFile(): YAMLFile? {
    val actionDescription = findActionDescription() ?: return null

    // Find the action file
    return service<RemoteActionManager>().getActionFile(actionDescription, element.containingFile?.originalFile ?: return null)
  }

  fun findActionDescription(): ActionDescription? {
    val step = GHAUtils.getStepFromInput(element) ?: return null
    return ActionDescription.fromString(step.getUses()?.textValue ?: return null)
  }
}
