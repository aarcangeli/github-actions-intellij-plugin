package com.github.aarcangeli.githubactions.domain

import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLMapping
import java.util.*

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
}
