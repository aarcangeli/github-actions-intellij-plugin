package com.github.aarcangeli.githubactions.domain

import com.github.aarcangeli.githubactions.utils.KeyTextCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.*

private val IS_STEP_PATTERN = PlatformPatterns.psiElement(YAMLMapping::class.java)
  .withParent(
    PlatformPatterns.psiElement(YAMLSequenceItem::class.java)
      .withParent(
        PlatformPatterns.psiElement(YAMLSequence::class.java)
          .withParent(
            PlatformPatterns.psiElement(YAMLKeyValue::class.java)
              .with(KeyTextCondition("steps"))
              .withParent(
                JobElement.getJobPattern()
              )
          )
      )
  )

class StepElement(private val step: YAMLMapping) {
  fun getRunAsText(): String? {
    return getRun()?.textValue
  }

  fun getRun(): YAMLScalar? {
    return step.getKeyValueByKey("run")?.value as? YAMLScalar
  }

  fun getUses(): YAMLScalar? {
    return step.getKeyValueByKey("uses")?.value as? YAMLScalar
  }

  /**
   * Guesses the shell used by this step.
   * @return The shell used by this step, or null if it could not be guessed.
   */
  fun guessShell(): ShellType? {
    val shell = step.getKeyValueByKey("shell")?.value as? YAMLScalar ?: return guessShellFromJob()
    return when (shell.textValue) {
      "cmd" -> ShellType.Cmd
      "sh" -> ShellType.Bash
      "bash" -> ShellType.Bash
      "powershell" -> ShellType.PowerShell
      "pwsh" -> ShellType.PowerShellCore
      "python" -> ShellType.Python
      else -> ShellType.Custom
    }
  }

  private fun guessShellFromJob(): ShellType? {
    when (getJob().guessRunner() ?: return null) {
      RunnerType.Linux -> return ShellType.Bash
      RunnerType.Windows -> return ShellType.PowerShellCore
      RunnerType.MacOs -> return ShellType.Bash
      // todo: guess from tags
      RunnerType.SelfHosted -> return null
    }
  }

  fun getName(): String? {
    return (step.getKeyValueByKey("name")?.value as? YAMLScalar)?.textValue
  }

  fun getElement(): PsiElement {
    return step
  }

  override fun toString(): String {
    return "Step: " + (getName() ?: getRun()?.textValue ?: "Unnamed")
  }

  private fun getJob(): JobElement {
    return step.parent?.parent?.parent?.parent?.let { JobElement(it as YAMLMapping) } ?: error("Step is not inside a job")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return step == (other as StepElement).step
  }

  override fun hashCode(): Int {
    return step.hashCode()
  }

  companion object {
    fun fromYaml(step: YAMLMapping): StepElement? {
      return if (isStep(step)) StepElement(step) else null
    }

    private fun isStep(step: YAMLMapping): Boolean {
      return IS_STEP_PATTERN.accepts(step)
    }
  }
}
