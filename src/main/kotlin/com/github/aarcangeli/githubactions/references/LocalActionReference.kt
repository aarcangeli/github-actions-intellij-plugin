package com.github.aarcangeli.githubactions.references

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.impl.source.resolve.ResolveCache
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Represents a reference to an action file.
 *
 * ES:
 * ```yaml
 * - uses: ./.github/actions/my-local-action
 *   with:
 *     my-input: value
 * ```
 */
class LocalActionReference(element: YAMLScalar) : PsiReferenceBase<YAMLScalar>(element, true) {
  override fun resolve(): PsiElement? {
    return ResolveCache.getInstance(element.project).resolveWithCaching(this, { _, _ -> resolveInner() }, false, false)
  }

  private fun resolveInner(): PsiElement? {
    val containingFile = element.containingFile?.originalFile ?: return null
    val description = ActionDescription.fromString(element.textValue)
    return service<RemoteActionManager>().getActionFile(description, containingFile)
  }
}
