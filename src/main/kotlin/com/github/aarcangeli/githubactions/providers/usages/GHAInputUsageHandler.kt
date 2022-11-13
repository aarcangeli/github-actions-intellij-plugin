package com.github.aarcangeli.githubactions.providers.usages

import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.openapi.application.QueryExecutorBase
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import org.jetbrains.yaml.psi.YAMLFile

/**
 * The default strategy on intellij platform is to search for usages based on the file name which is always `action`.
 *
 * This is not good for action files are they are referenced by the directory name instead.
 *
 * @see com.intellij.psi.impl.search.CachesBasedRefSearcher
 */
class GHAInputUsageHandler : QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>(true) {
  override fun processQuery(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>) {
    val file = queryParameters.elementToSearch as? YAMLFile ?: return
    if (!GHAUtils.isActionFile(file)) {
      return
    }

    val virtualFile = file.virtualFile ?: return
    val directoryName = virtualFile.parent?.name ?: return
    queryParameters.optimizer.searchWord(directoryName, queryParameters.effectiveSearchScope, true, file)
  }
}
