package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLKeyValue

class GHAUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    val inputDefinition = element as? YAMLKeyValue ?: return null
    if (!GHAUtils.isInputDefinition(inputDefinition)) {
      return null
    }

    // an input can be used in all the workflow files
    return GlobalSearchScope.getScopeRestrictedByFileTypes(
      GlobalSearchScope.allScope(element.project),
      YAMLFileType.YML
    )
  }
}
