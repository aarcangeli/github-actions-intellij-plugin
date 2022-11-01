package com.github.aarcangeli.githubactions.domain

import com.github.aarcangeli.githubactions.utils.KeyTextCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence

private val IS_JOB_PATTERN = PlatformPatterns.psiElement(YAMLMapping::class.java)
  .withParent(
    PlatformPatterns.psiElement(YAMLKeyValue::class.java)
      .withParent(
        PlatformPatterns.psiElement(YAMLMapping::class.java)
          .withParent(
            PlatformPatterns.psiElement(YAMLKeyValue::class.java)
              .with(KeyTextCondition("jobs"))
              .withParent(
                WorkflowElement.getWorkflowPattern()
              )
          )
      )
  )

class JobElement(private val job: YAMLMapping) {

  fun getSteps(): List<StepElement> {
    val steps = job.getKeyValueByKey("steps")?.value as? YAMLSequence ?: return emptyList()
    val allSteps = ArrayList<StepElement>()
    for (step in steps.items) {
      allSteps.add(StepElement(step.value as? YAMLMapping ?: continue))
    }
    return allSteps
  }

  fun guessRunner(): RunnerType? {
    val value = (job.getKeyValueByKey("runs-on")?.value as? YAMLScalar ?: return null).textValue
    if (value.startsWith("windows-")) {
      return RunnerType.Windows
    }
    if (value.startsWith("ubuntu-")) {
      return RunnerType.Linux
    }
    if (value.startsWith("macos-")) {
      return RunnerType.MacOs
    }
    if (value == "self-hosted") {
      return RunnerType.SelfHosted
    }
    // todo: try to guess from expressions eg: ${{ matrix.os }}
    return null
  }

  fun getName(): String? {
    return (job.getKeyValueByKey("name")?.value as? YAMLScalar)?.textValue
  }

  override fun toString(): String {
    return "Job: " + (getName() ?: "Unnamed")
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return job == (other as JobElement).job
  }

  override fun hashCode(): Int {
    return job.hashCode()
  }

  companion object {
    fun fromYaml(step: YAMLMapping): JobElement? {
      return if (isJob(step)) JobElement(step) else null
    }

    fun getJobPattern(): PsiElementPattern.Capture<YAMLMapping> {
      return IS_JOB_PATTERN
    }

    fun isJob(job: YAMLMapping): Boolean {
      return IS_JOB_PATTERN.accepts(job)
    }
  }
}
