package com.github.aarcangeli.githubactions.providers.inspections

import com.github.aarcangeli.githubactions.GHABundle
import com.github.aarcangeli.githubactions.commands.ActionCommand
import com.github.aarcangeli.githubactions.domain.ShellType
import com.github.aarcangeli.githubactions.domain.StepElement
import com.github.aarcangeli.githubactions.domain.WorkflowElement
import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.codeInspection.*
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiFileRange
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLQuotedText
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLScalarList

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
    if (!GHAUtils.isWorkflowPath(file.virtualFile ?: return null)) return null
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
    val textValue = run.textValue
    for (line in textValue.split("\n")) {
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

      val range =
        TextRange(CharArrayUtil.shiftForward(run.text, start, " \t\n"), CharArrayUtil.shiftBackward(run.text, end - 1, " \t\n") + 1)
      val rangePointer =
        SmartPointerManager.getInstance(run.project)
          .createSmartPsiFileRangePointer(run.containingFile, range.shiftRight(run.textRange.startOffset))

      // some scalar implementation are bugged and return the wrong offset, don't create a quick fix in this case
      var createQuickFix = false
      if (run is YAMLScalarList || run is YAMLQuotedText) {
        createQuickFix = true
      }
      else if (!textValue.contains("\n")) {
        createQuickFix = true
      }
      val replaceQuickFix = if (createQuickFix) ReplaceDeprecatedCommandQuickFix(rangePointer, commandAction) else null

      val problemDescriptor: ProblemDescriptor = manager.createProblemDescriptor(
        run,
        range,
        GHABundle.message("github.actions.deprecated.command.used", commandAction.command.command, commandAction.url),
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        false, replaceQuickFix
      )

      result.add(problemDescriptor)
    }

    return result
  }

  private fun parseDeprecatedCommand(command: String, shellType: ShellType): SetDeprecatedCommand? {
    val echo = readEcho(command, shellType) ?: return null
    val actionCommand = ActionCommand.tryParse(echo) ?: return null
    if (actionCommand.command in listOf("save-state", "set-output")) {
      return SetDeprecatedCommand(actionCommand, REF_URL_OUTPUT, shellType)
    }
    if (actionCommand.command in listOf("set-env", "add-path")) {
      return SetDeprecatedCommand(actionCommand, REF_URL_ENV, shellType)
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

      // TODO: for now we ignore escape characters and shell specific features.
      if (text.startsWith('"') && text.endsWith('"')) {
        return text.substring(1, text.length - 1)
      }
      if (text.startsWith("'") && text.endsWith("'")) {
        return text.substring(1, text.length - 1)
      }
    }

    if (shellType == ShellType.PowerShell || shellType == ShellType.PowerShellCore) {
      if (command.startsWith("Write-Output ")) {
        val text = command.substring(13).trim()
        if (text.startsWith('"') && text.endsWith('"')) {
          return text.substring(1, text.length - 1)
        }
        if (text.startsWith("'") && text.endsWith("'")) {
          return text.substring(1, text.length - 1)
        }
      }
    }

    return null
  }

}

class ReplaceDeprecatedCommandQuickFix(private val rangePointer: SmartPsiFileRange, private val commandAction: SetDeprecatedCommand) :
  LocalQuickFix {

  override fun getName(): String {
    val newFile = when (commandAction.command.command) {
      "save-state" -> "\$GITHUB_STATE"
      "set-output" -> "\$GITHUB_OUTPUT"
      "set-env" -> "\$GITHUB_ENV"
      "add-path" -> "\$GITHUB_PATH"
      else -> return familyName
    }
    return GHABundle.message("github.actions.deprecated.command.quickfix", newFile)
  }

  override fun getFamilyName(): String {
    return "Replace command"
  }

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val run = descriptor.psiElement as YAMLScalar
    val range = rangePointer.range ?: return
    val newText = when (commandAction.command.command) {
      "save-state" -> composeEcho(
        "${commandAction.command.getProperty("name")}=${commandAction.command.data ?: ""}",
        "GITHUB_STATE",
        commandAction.shellType
      )

      "set-output" -> composeEcho(
        "${commandAction.command.getProperty("name")}=${commandAction.command.data ?: ""}",
        "GITHUB_OUTPUT",
        commandAction.shellType
      )

      "set-env" -> composeEcho(
        "${commandAction.command.getProperty("name")}=${commandAction.command.data ?: ""}",
        "GITHUB_ENV",
        commandAction.shellType
      )

      "add-path" -> composeEcho(commandAction.command.data ?: "", "GITHUB_PATH", commandAction.shellType)
      else -> return
    }
    ElementManipulators.handleContentChange(run, TextRange.create(range).shiftLeft(run.textRange.startOffset), newText)
  }

  private fun composeEcho(content: String, destination: String, shellType: ShellType): String {
    if (shellType == ShellType.Cmd) {
      return "echo $content>>%$destination%"
    }
    if (shellType == ShellType.PowerShell || shellType == ShellType.PowerShellCore) {
      return "\"$content\" >> \$env:$destination"
    }
    return "echo \"$content\" >> \$$destination"
  }
}

class SetDeprecatedCommand(val command: ActionCommand, val url: String, val shellType: ShellType)
