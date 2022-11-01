package com.github.aarcangeli.githubactions.utils

import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
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
}
