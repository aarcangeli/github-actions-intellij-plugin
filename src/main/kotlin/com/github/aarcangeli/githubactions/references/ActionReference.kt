package com.github.aarcangeli.githubactions.references

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.SyntheticElement
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.impl.source.resolve.ResolveCache
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Represents a reference to an action file.
 *
 * ES:
 * ```yaml
 * - uses: actions/checkout@v2
 * - uses: ./.github/actions/my-local-action
 * ```
 */
class ActionReference(element: YAMLScalar) : PsiReferenceBase<YAMLScalar>(element, true) {
  override fun resolve(): PsiElement? {
    return ResolveCache.getInstance(element.project).resolveWithCaching(this, { _, _ -> resolveInner() }, false, false)
  }

  private fun resolveInner(): PsiElement? {
    val containingFile = element.containingFile?.originalFile ?: return null
    val description = ActionDescription.fromString(element.textValue)

    // docker actions redirects to the docker hub if available
    // see WebReference
    if (description.isDocker()) {
      description.toUrl()?.let {
        return MyUrlPsiElement(it)
      }
      return null
    }

    return service<RemoteActionManager>().getActionFile(description, containingFile)
  }

  // A psi element which redirects to an url
  internal inner class MyUrlPsiElement(private val url: String) : FakePsiElement(), SyntheticElement {
    override fun getParent(): PsiElement {
      return myElement
    }

    override fun navigate(requestFocus: Boolean) {
      BrowserUtil.browse(url)
    }

    override fun getPresentableText(): String {
      return url
    }

    override fun getName(): String {
      return url.split("/").last()
    }

    override fun getTextRange(): TextRange {
      val rangeInElement: TextRange = rangeInElement
      val elementRange: TextRange = myElement.textRange
      return rangeInElement.shiftRight(elementRange.startOffset)
    }

    override fun getUseScope(): SearchScope {
      return GlobalSearchScope.getScopeRestrictedByFileTypes(
        GlobalSearchScope.allScope(element.project),
        YAMLFileType.YML
      )
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as MyUrlPsiElement
      return url == other.url
    }

    override fun hashCode(): Int {
      return url.hashCode()
    }
  }

}
