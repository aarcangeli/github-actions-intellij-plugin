package com.github.aarcangeli.githubactions.utils

import com.github.aarcangeli.githubactions.domain.StepElement
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

object GHAUtils {
  /**
   * A workflow path is a file that is named `*.yml` or `*.yaml` and is located in the `.github/workflows` directory.
   * ref: https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#about-yaml-syntax-for-workflows
   */
  fun isWorkflowPath(file: VirtualFile): Boolean {
    if (file.isDirectory || file.extension != "yml" && file.extension != "yaml") {
      return false
    }
    val workflows = file.parent
    if (workflows == null || workflows.name != "workflows") {
      return false
    }
    val github = workflows.parent
    if (github == null || github.name != ".github") {
      return false
    }

    // skip if the root directory is not a git repository
    return true
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
}
