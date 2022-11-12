package com.github.aarcangeli.githubactions.actions

import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile

interface RemoteActionManager {
  /**
   * Retrieve the action status for the given uses.
   * @param description The action name as specified in the "uses" field of workflow file.
   * @param file the file which contains the action, the analyzer daemon is automatically restarted when the state changes.
   */
  fun getActionStatus(description: ActionDescription, file: PsiFile): ActionStatus

  /**
   * Retrieve the action file for the given uses.
   * @param description The action name as specified in the "uses" field of workflow file.
   * @param file the file which contains the action, the analyzer daemon is automatically restarted when the state changes.
   * @return the action file or null if the action is not found, or it is currently being downloaded.
   */
  fun getActionFile(description: ActionDescription, file: PsiFile): YAMLFile?

  /**
   * Fetch the file for the given uses.
   */
  fun refreshAction(uses: String)

  /**
   * All failed fetches will be retried on next call to [getActionStatus].
   */
  fun retryAllFailedActions()
}

enum class ActionStatus {
  /** We cannot provide any information about the action.  */
  UNKNOWN,

  /** Cannot fetch action file for a network issue.  */
  FAILED,

  /** The service is currently fetching the result */
  IN_PROGRESS,

  /** The action is valid and ready to be used. */
  OK,

  /** The action doesn't exist or an error occurred while fetching it. */
  ACTION_NOT_FOUND,

  /** The action exist but the ref is invalid. */
  ACTION_REVISION_NOT_FOUND,
}
