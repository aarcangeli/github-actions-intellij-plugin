package com.github.aarcangeli.githubactions.providers.documentation

import com.github.aarcangeli.githubactions.actions.ActionDescription
import com.github.aarcangeli.githubactions.actions.RemoteActionManager
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.openapi.components.service
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Provides documentation for uses
 */
class GHAUsesDocumentationProvider : DocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): @Nls String? {
    val file = element.containingFile?.originalFile ?: return null
    val usesElement = element.parentOfType<YAMLScalar>() ?: return null
    val step = GHAUtils.getStepFromUses(usesElement) ?: return null

    val actionDescription = ActionDescription.fromString(step.getUses()?.textValue ?: return null)
    val actionFile = service<RemoteActionManager>().getActionFile(actionDescription, file) ?: return null
    val description = findDescription(actionFile) ?: return null

    return GHAUtils.renderMarkdown(description);
  }

  private fun findDescription(actionFile: YAMLFile): String? {
    for (document in actionFile.documents) {
      val root = document.topLevelValue as? YAMLMapping ?: continue
      return root.getKeyValueByKey("description")?.valueText ?: continue
    }
    return null
  }
}
