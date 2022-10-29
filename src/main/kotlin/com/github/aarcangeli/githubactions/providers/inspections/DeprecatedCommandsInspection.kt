package com.github.aarcangeli.githubactions.providers.inspections

import com.github.aarcangeli.githubactions.GHABundle
import com.github.aarcangeli.githubactions.commands.ActionCommand
import com.github.aarcangeli.githubactions.domain.ShellType
import com.github.aarcangeli.githubactions.domain.StepElement
import com.github.aarcangeli.githubactions.domain.WorkflowElement
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile

private const val REF_URL_OUTPUT = "https://github.blog/changelog/2022-10-11-github-actions-deprecating-save-state-and-set-output-commands/"
private const val REF_URL_ENV = "https://github.blog/changelog/2020-10-01-github-actions-deprecating-set-env-and-add-path-commands/"

/**
 * Inspect deprecated commands in the workflow file.
 * Ex:
 *   echo "::save-state name=MY_NAME::MY_VALUE"
 *   echo "::set-output name=MY_NAME::MY_VALUE"
 */
class DeprecatedCommandsInspection : LocalInspectionTool() {
  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
    if (file !is YAMLFile) return null
    return checkWorkflow(manager, WorkflowElement(file)).toTypedArray()
  }

  private fun checkWorkflow(manager: InspectionManager, workflow: WorkflowElement): List<ProblemDescriptor> {
    val result = mutableListOf<ProblemDescriptor>()

    for (job in workflow.getJobs()) {
      for (step in job.getSteps()) {
        ProgressManager.checkCanceled()
        result.addAll(checkDeprecatedCommand(manager, step))
      }
    }

    return result
  }

  private fun checkDeprecatedCommand(manager: InspectionManager, step: StepElement): List<ProblemDescriptor> {
    // Get run field if available
    val run = step.getRun() ?: return emptyList()
    val literalTextEscaper = run.createLiteralTextEscaper()
    val relevantTextRange = literalTextEscaper.relevantTextRange

    val shellType = step.guessShell() ?: ShellType.Bash

    // only simple shell are supported
    if (shellType !in listOf(ShellType.Cmd, ShellType.Bash, ShellType.PowerShell, ShellType.PowerShellCore)) {
      return emptyList()
    }

    val result = mutableListOf<ProblemDescriptor>()

    var offset = 0
    for (line in run.textValue.split("\n")) {
      ProgressManager.checkCanceled()
      val lineOffset = offset
      offset += line.length + 1

      val command = line.trim()
      if (command.isEmpty() || command.startsWith('#')) {
        continue
      }

      // Finds the deprecated command
      val commandAction = parseDeprecatedCommand(command, shellType) ?: continue

      // inject offset to host element
      val start = literalTextEscaper.getOffsetInHost(lineOffset, relevantTextRange)
      val end = literalTextEscaper.getOffsetInHost(lineOffset + line.length, relevantTextRange)
      if (start == -1 || end == -1) {
        continue
      }

      val problemDescriptor: ProblemDescriptor = manager.createProblemDescriptor(
        run,
        TextRange(start, end),
        GHABundle.message("github.actions.deprecated.command.used", commandAction.command.command, commandAction.url),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        false
      )

      result.add(problemDescriptor)
    }

    return result
  }

  private fun parseDeprecatedCommand(command: String, shellType: ShellType): SetDeprecatedCommand? {
    val echo = readEcho(command, shellType) ?: return null
    val actionCommand = ActionCommand.tryParse(echo) ?: return null
    if (actionCommand.command in listOf("save-state", "set-output")) {
      return SetDeprecatedCommand(actionCommand, REF_URL_OUTPUT)
    }
    if (actionCommand.command in listOf("set-env", "add-path")) {
      return SetDeprecatedCommand(actionCommand, REF_URL_ENV)
    }
    return null
  }

  private fun readEcho(command: String, shellType: ShellType): String? {
    if (command.startsWith("echo ")) {
      val text = command.substring(5).trim()
      if (shellType == ShellType.Cmd) {
        // On cmd, echo prints the raw text.
        // However, if the command is wrapped in double quotes, we still remove them as the user may have used the wrong shell.
        if (text.startsWith("\"") && text.endsWith("\"")) {
          return text.substring(1, text.length - 1)
        }
        return text
      }

      // TODO: we currently ignore escape characters for now or shell specific features.
      if (text.startsWith('"') && text.endsWith('"')) {
        return text.substring(1, text.length - 1)
      }
      if (text.startsWith("'") && text.endsWith("'")) {
        return text.substring(1, text.length - 1)
      }
    }

    return null
  }

  class SetDeprecatedCommand(val command: ActionCommand, val url: String)

}
