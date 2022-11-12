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

    val definition = DEFINITION_START + "input <b>" + yamlKeyValue.keyText + "</b>" + DEFINITION_END

    // get description
    val description = mapping.getKeyValueByKey("description")?.valueText ?: return null

    val document: Node = Parser.builder().build().parse(description)
    val renderer: HtmlRenderer = HtmlRenderer.builder().build()
    val content = CONTENT_START + renderer.render(document) + CONTENT_END

    return definition + content
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
