package com.github.aarcangeli.githubactions.actions

import com.intellij.util.text.nullize

private val ALPHANUMERIC_PATTERN = Regex("[a-zA-Z0-9\\-._]+")
private val TAG_PATTERN = Regex("[a-zA-Z0-9\\-./_]+")
private val DOCKER_TAG_PATTERN = Regex("[a-zA-Z0-9\\-._]+")

/**
 * Represents a GitHub Action like:
 * - `owner/repo/path@ref`
 * - `actions/checkout@v2`
 * - `./.github/actions/my-action`
 *
 * [Documentation](https://docs.github.com/en/actions/using-workflows/workflow-syntax-for-github-actions#example-using-a-public-action)
 */
class ActionDescription private constructor(
  val owner: String?,
  val repo: String?,
  val path: String?,
  val ref: String?,
  val dockerPath: String?,
  val dockerTag: String?,
) {

  /**
   * Returns the full name of the action, like `owner/repo`
   */
  fun getFullName(): String {
    assert(isStandardAction())
    return if (owner != null && repo != null) "$owner/$repo" else "$owner"
  }

  fun isDocker(): Boolean {
    return dockerPath != null
  }

  fun isLocalPath(): Boolean {
    return owner == null && repo == null && path != null
  }

  fun isStandardAction(): Boolean {
    return !isDocker() && !isLocalPath()
  }

  override fun toString(): String {
    if (isDocker()) {
      return if (dockerTag != null) "docker://$dockerPath:$dockerTag" else "docker://$dockerPath"
    }
    if (isLocalPath()) {
      return path!!
    }
    return "$owner/$repo${if (path != null) "/$path" else ""}@$ref"
  }

  fun isValid(ignoreRef: Boolean = false, ignoreDockerTag: Boolean = false): Boolean {
    if (isDocker()) {
      // docker tag is optional
      return dockerPath != null && dockerPath.isTrimmed() && dockerPath.isNotEmpty() &&
        (ignoreDockerTag || isDockerTagValid())
    }
    if (isLocalPath()) {
      // no particular validation for local paths
      return true
    }
    // validate owner, repo, path and ref
    if (!owner.isTrimmed() || !repo.isTrimmed() || path != null && !path.isTrimmed()) {
      return false
    }
    if (!ignoreRef && !isRefValid()) {
      return false
    }
    return owner!!.matches(ALPHANUMERIC_PATTERN) &&
      repo!!.matches(ALPHANUMERIC_PATTERN)
  }

  fun isDockerTagValid(): Boolean {
    if (isDocker()) {
      return dockerTag == null || dockerTag.matches(DOCKER_TAG_PATTERN)
    }
    return true
  }

  fun isRefValid(): Boolean {
    if (isStandardAction()) {
      return ref != null && ref.matches(TAG_PATTERN)
    }
    return true
  }

  private fun String?.isTrimmed(): Boolean {
    return this != null && this.trim() == this
  }

  fun toUrl(): String? {
    if (isDocker()) {
      if (!dockerPath!!.contains("/")) {
        // Docker hub
        return "https://hub.docker.com/_/$dockerPath"
      }
      if (dockerPath.startsWith("ghcr.io/") && dockerPath.count { it == '/' } == 2 && isValid()) {
        // GitHub container registry
        return "https://$dockerPath"
      }
      if (dockerPath.startsWith("gcr.io/") && dockerPath.count { it == '/' } == 2 && isValid()) {
        // google container registry
        return "https://$dockerPath"
      }
    }
    if (isStandardAction()) {
      if (path != null) {
        return "https://github.com/$owner/$repo/tree/${ref ?: "HEAD"}/$path"
      }
      if (ref != null) {
        return "https://github.com/$owner/$repo/tree/$ref"
      }
      if (owner != null && repo != null) {
        return "https://github.com/$owner/$repo/tree/$ref"
      }
    }
    return null
  }

  fun replaceRef(newRef: String): ActionDescription {
    assert(isStandardAction())
    return ActionDescription(owner, repo, path, newRef, dockerPath, dockerTag)
  }

  companion object {
    fun fromString(text: String): ActionDescription {
      // docker image
      if (text.startsWith("docker://")) {
        val docker = text.substringAfter("docker://")
        val dockerPath = docker.substringBefore(":")
        val dockerTag = docker.substringAfter(":", "").nullize()
        return ActionDescription(null, null, null, null, dockerPath, dockerTag)
      }

      // local path
      if (text.startsWith("./")) {
        return ActionDescription(null, null, text, null, null, null)
      }

      // standard action
      val parts = text.split("@")
      val fullName = parts[0].split("/")
      val owner = fullName[0]
      val repo = fullName.getOrNull(1).nullize()
      val path = fullName.drop(2).joinToString("/").nullize()
      val ref = parts.getOrNull(1).nullize()
      return ActionDescription(owner, repo, path, ref, null, null)
    }
  }
}
