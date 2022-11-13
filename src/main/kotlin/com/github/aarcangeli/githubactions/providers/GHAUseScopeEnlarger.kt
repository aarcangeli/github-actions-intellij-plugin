package com.github.aarcangeli.githubactions.providers

import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UseScopeEnlarger
import org.jetbrains.yaml.YAMLFileType
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue

/**
 * Input and action files can be used in all the workflow files
 */
class GHAUseScopeEnlarger : UseScopeEnlarger() {
  override fun getAdditionalUseScope(element: PsiElement): SearchScope? {
    if (element is YAMLKeyValue && GHAUtils.isInputDefinition(element) || element is YAMLFile && GHAUtils.isActionFile(element)) {
      return GlobalSearchScope.getScopeRestrictedByFileTypes(
        GlobalSearchScope.allScope(element.project),
        YAMLFileType.YML
      )
    }

    return null
  }
}
