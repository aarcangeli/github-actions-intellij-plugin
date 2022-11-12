package com.github.aarcangeli.githubactions.domain

import com.github.aarcangeli.githubactions.utils.GHAUtils
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns
import com.intellij.patterns.PsiElementPattern
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import java.util.*

private val IS_WORKFLOW_PATTERN = PlatformPatterns.psiElement(YAMLMapping::class.java)
  .withParent(
    PlatformPatterns.psiElement(YAMLDocument::class.java)
      .withParent(
        PlatformPatterns.psiElement(YAMLFile::class.java)
          .with(object : PatternCondition<YAMLFile>("Is Workflow File") {
            override fun accepts(t: YAMLFile, context: ProcessingContext?): Boolean {
              return GHAUtils.isWorkflowPath(t.originalFile.virtualFile ?: return false)
            }
          })
      )
  )

/**
 * Wrapper class for a workflow file (YAMLFile).
 */
class WorkflowElement(private val file: YAMLFile) {

  fun getJobs(): List<JobElement> {
    val allJobs = ArrayList<JobElement>()
    for (document in file.documents) {
      val root = document.topLevelValue as? YAMLMapping ?: continue
      val jobs = root.getKeyValueByKey("jobs")?.value as? YAMLMapping ?: continue
      for (job in jobs.keyValues) {
        allJobs.add(JobElement(job.value as? YAMLMapping ?: continue))
      }
    }
    return Collections.unmodifiableList(allJobs)
  }

  override fun toString(): String {
    return "Workflow: " + file.name
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    return file == (other as WorkflowElement).file
  }

  override fun hashCode(): Int {
    return file.hashCode()
  }

  companion object {
    fun getWorkflowPattern(): PsiElementPattern.Capture<YAMLMapping> {
      return IS_WORKFLOW_PATTERN
    }

    fun isWorkflowFile(file: YAMLMapping): Boolean {
      return IS_WORKFLOW_PATTERN.accepts(file)
    }
  }
}
