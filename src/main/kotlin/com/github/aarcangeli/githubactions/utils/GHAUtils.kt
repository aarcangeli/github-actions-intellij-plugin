package com.github.aarcangeli.githubactions.utils

import com.github.aarcangeli.githubactions.domain.StepElement
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

object GHAUtils {
  fun isWorkflowPath(file: VirtualFile): Boolean {
    return getGitRoot(file) != null
  }

  /**
   * A workflow path is a file that is named `*.yml` or `*.yaml` and is located in the `.github/workflows` directory.
   * ref: https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#about-yaml-syntax-for-workflows
   */
  fun getGitRoot(file: VirtualFile): VirtualFile? {
    if (file.isDirectory || file.extension != "yml" && file.extension != "yaml") {
      return null
    }
    val workflows = file.parent
    if (workflows == null || workflows.name != "workflows") {
      return null
    }
    val github = workflows.parent
    if (github == null || github.name != ".github") {
      return null
    }

    return github.parent
  }

  fun findRange(scalar: YAMLScalar, textRange: TextRange): TextRange {
    val escaper = scalar.createLiteralTextEscaper()
    val relevantTextRange = escaper.relevantTextRange
    val start = escaper.getOffsetInHost(textRange.startOffset, relevantTextRange)
    val end = escaper.getOffsetInHost(textRange.endOffset, relevantTextRange)
    return TextRange(start, end)
  }

  fun findInputs(actionFile: YAMLFile): Map<String, YAMLMapping> {
    val result = mutableMapOf<String, YAMLMapping>()
    for (document in actionFile.documents) {
      val root = document.topLevelValue as? YAMLMapping ?: continue
      val jobs = root.getKeyValueByKey("inputs")?.value as? YAMLMapping ?: continue
      for (job in jobs.keyValues) {
        if (job.keyText.isNotEmpty()) {
          result[job.keyText] = job.value as? YAMLMapping ?: continue
        }
      }
    }
    return result
  }

  fun getStepFromInput(element: YAMLKeyValue): StepElement? {
    val withElement = element.parent as? YAMLMapping ?: return null
    val with = withElement.parent as? YAMLKeyValue ?: return null
    if (with.keyText != "with") return null
    val step = with.parent as? YAMLMapping ?: return null
    return StepElement.fromYaml(step)
  }

  fun getStepFromUses(element: YAMLScalar): StepElement? {
    val uses = element.parent as? YAMLKeyValue ?: return null
    if (uses.keyText != "uses") return null
    val step = uses.parent as? YAMLMapping ?: return null
    return StepElement.fromYaml(step)
  }

  fun renderMarkdown(description: String): String {
    val document: Node = Parser.builder().build().parse(description)
    val renderer: HtmlRenderer = HtmlRenderer.builder().build()
    return renderer.render(document)
  }
}
