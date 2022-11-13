package com.github.aarcangeli.githubactions.providers.documentation

import com.github.aarcangeli.githubactions.references.InputPropertyReference
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parents
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Provides documentation for input parameters
 */
class GHAInputDocumentationProvider : DocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): @Nls String? {
    val yamlKeyValue: YAMLKeyValue = findActionInputRoot(originalElement ?: element) ?: return null
    val mapping = yamlKeyValue.value as? YAMLMapping ?: return null

    return buildString {
      // Append definition
      append(DEFINITION_START + "input <b>" + yamlKeyValue.keyText + "</b>" + DEFINITION_END)

      // Append description
      val description = mapping.getKeyValueByKey("description")?.valueText ?: return null
      append(CONTENT_START + GHAUtils.renderMarkdown(description) + CONTENT_END)

      append(SECTIONS_START)

      // Add default value
      mapping.getKeyValueByKey("default")?.valueText?.let {
        append(SECTION_HEADER_START + "Default" + SECTION_SEPARATOR + "<code>$it</code>" + SECTION_END)
      }

      // Add default value
      (mapping.getKeyValueByKey("required")?.valueText ?: "false").let {
        append(SECTION_HEADER_START + "Required" + SECTION_SEPARATOR + it + SECTION_END)
      }

      // Add default value
      mapping.getKeyValueByKey("deprecationMessage")?.valueText?.let {
        append(SECTION_HEADER_START + "Deprecated" + SECTION_SEPARATOR + it + SECTION_END)
      }

      append(SECTIONS_END)
    }
  }

  private fun findActionInputRoot(element: PsiElement): YAMLKeyValue? {
    // element already be the input we want
    if (element is YAMLKeyValue) {
      if (GHAUtils.isInputDefinition(element)) {
        return element
      }
    }

    val keyValue = element.parentOfType<YAMLKeyValue>() ?: return null

    // element must be the key of the input
    if (!element.parents(true).contains(keyValue.key)) {
      return null
    }

    val reference = keyValue.references.find { it is InputPropertyReference } ?: return null
    return reference.resolve() as? YAMLKeyValue
  }
}
