package com.github.aarcangeli.githubactions.providers.documentation

import com.github.aarcangeli.githubactions.references.InputPropertyReference
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jetbrains.annotations.Nls
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

class GHADocumentationProvider : DocumentationProvider {
  override fun generateDoc(element: PsiElement, originalElement: PsiElement?): @Nls String? {
    val mapping: YAMLMapping = findActionInputRoot(originalElement ?: element) ?: return null
    val yamlKeyValue = mapping.parent as? YAMLKeyValue ?: return null

    return buildString {
      // Append definition
      append(DEFINITION_START + "input <b>" + yamlKeyValue.keyText + "</b>" + DEFINITION_END)

      // Append description
      val description = mapping.getKeyValueByKey("description")?.valueText ?: return null
      val document: Node = Parser.builder().build().parse(description)
      val renderer: HtmlRenderer = HtmlRenderer.builder().build()
      append(CONTENT_START + renderer.render(document) + CONTENT_END)

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

  private fun findActionInputRoot(element: PsiElement): YAMLMapping? {
    // element already be the input we want
    if (element is YAMLMapping) {
      if (element.getKeyValueByKey("description") != null) {
        return element
      }
    }

    val keyValue = element.parentOfType<YAMLKeyValue>() ?: return null
    val reference = keyValue.references.find { it is InputPropertyReference } ?: return null
    return reference.resolve() as? YAMLMapping
  }
}
