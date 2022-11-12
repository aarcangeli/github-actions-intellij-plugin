package com.github.aarcangeli.githubactions.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.impl.http.FileDownloadingListener
import com.intellij.openapi.vfs.impl.http.HttpVirtualFile
import com.intellij.openapi.vfs.impl.http.RemoteFileState
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.FileContentUtilCore
import org.jetbrains.yaml.psi.YAMLFile
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val LOG: Logger = LoggerFactory.getLogger(RemoteActionManagerImpl::class.java)
private val LISTENER_ATTACHED = Key.create<RemoteActionManagerImpl.DownloadListener>("LISTENER_ATTACHED")
private val LAST_TRY_VERSION = Key.create<Long>("LAST_TRY_VERSION")

private const val BACKGROUND_WAIT_TIMEOUT = 10000

class RemoteActionManagerImpl : Disposable, RemoteActionManager {
  private val retryFailedFetches = SimpleModificationTracker()

  override fun getActionStatus(description: ActionDescription, file: PsiFile): ActionStatus {
    if (!description.isValid()) return ActionStatus.UNKNOWN

    val actionFile = retrieveFileForUses(description) ?: return ActionStatus.UNKNOWN

    if (actionFile is HttpVirtualFile) {
      return getStatusFromHttpActionFile(actionFile, description, file)
    }

    getYamlActionFromFile(file.project, actionFile) ?: return ActionStatus.UNKNOWN
    return ActionStatus.OK
  }

  override fun getActionFile(description: ActionDescription, file: PsiFile): YAMLFile? {
    if (!description.isValid()) return null

    val actionFile = retrieveFileForUses(description) ?: return null

    if (actionFile is HttpVirtualFile) {
      return getYamlActionFromHttpFile(actionFile, file)
    }

    return getYamlActionFromFile(file.project, actionFile)
  }

  override fun refreshAction(uses: String) {
    val description = ActionDescription.fromString(uses)
    val virtualFile = retrieveFileForUses(description) as? HttpVirtualFile ?: return

    LOG.info("Refresh file: ${virtualFile.url}")
    virtualFile.refresh(true, false)
    virtualFile.putUserData(LAST_TRY_VERSION, retryFailedFetches.modificationCount)

    if (description.ref != "HEAD") {
      retrieveFileForUses(description.replaceRef("HEAD"))?.let {
        LOG.info("Refresh file: ${virtualFile.url}")
        it.refresh(true, false)
        it.putUserData(LAST_TRY_VERSION, retryFailedFetches.modificationCount)
      }
    }
  }

  private fun getStatusFromHttpActionFile(actionFile: HttpVirtualFile, description: ActionDescription, file: PsiFile): ActionStatus {
    tryToFetch(actionFile)

    // wait for download to finish
    waitDownloadOrInstallListener(actionFile, file)

    val fileInfo = actionFile.fileInfo ?: return ActionStatus.UNKNOWN
    return when (fileInfo.state) {
      RemoteFileState.DOWNLOADING_IN_PROGRESS -> ActionStatus.IN_PROGRESS

      // the resource has been downloaded, verify if it
      RemoteFileState.DOWNLOADED -> {
        getYamlActionFromFile(file.project, actionFile) ?: return ActionStatus.UNKNOWN
        return ActionStatus.OK
      }

      // the action revision is not found, but the action may still be available
      RemoteFileState.ERROR_OCCURRED -> {
        if (isHttp404(fileInfo.errorMessage)) {
          if (description.isStandardAction()) {
            if (description.ref == "HEAD") return ActionStatus.ACTION_NOT_FOUND
            // fetch the result of the HEAD revision
            return when (val headStatus = getActionStatus(description.replaceRef("HEAD"), file)) {
              ActionStatus.OK -> ActionStatus.ACTION_REVISION_NOT_FOUND
              else -> headStatus
            }
          }

          ActionStatus.ACTION_NOT_FOUND
        }

        return ActionStatus.FAILED
      }

      else -> return ActionStatus.UNKNOWN
    }
  }

  private fun getYamlActionFromHttpFile(actionFile: HttpVirtualFile, file: PsiFile): YAMLFile? {
    tryToFetch(actionFile)

    // wait for download to finish
    waitDownloadOrInstallListener(actionFile, file)

    val fileInfo = actionFile.fileInfo ?: return null
    if (fileInfo.state == RemoteFileState.DOWNLOADED) {
      return getYamlActionFromFile(file.project, actionFile)
    }

    return null
  }

  private fun tryToFetch(actionFile: HttpVirtualFile) {
    val fileInfo = actionFile.fileInfo ?: return

    // start download
    if (fileInfo.state == RemoteFileState.DOWNLOADING_NOT_STARTED) {
      LOG.info("Started downloading: ${actionFile.url}")
      fileInfo.startDownloading()
      actionFile.putUserData(LAST_TRY_VERSION, retryFailedFetches.modificationCount)
    }

    // retry if version changed
    if (fileInfo.state == RemoteFileState.ERROR_OCCURRED) {
      actionFile.getUserData(LAST_TRY_VERSION)?.let {
        if (it != retryFailedFetches.modificationCount) {
          LOG.info("Restarting download: ${actionFile.url}")
          fileInfo.restartDownloading()
          actionFile.putUserData(LAST_TRY_VERSION, retryFailedFetches.modificationCount)
        }
      }
    }
  }

  private fun isHttp404(errorMessage: String?): Boolean {
    // very basic check, but it should be enough
    return errorMessage?.contains("Request failed with status code 404") ?: false
  }

  private fun waitDownloadOrInstallListener(actionFile: HttpVirtualFile, workflowFile: PsiFile) {
    val fileInfo = actionFile.fileInfo ?: return

    // Never wait on EDT
    if (ApplicationManager.getApplication().isDispatchThread) {
      LOG.warn("Tried to fetch a file in EDT")
      installListenerIfNeeded(actionFile, workflowFile)
      return
    }

    val millis = System.currentTimeMillis()
    while (fileInfo.state == RemoteFileState.DOWNLOADING_IN_PROGRESS) {
      ProgressManager.checkCanceled()

      if (System.currentTimeMillis() - millis > BACKGROUND_WAIT_TIMEOUT) {
        LOG.warn("Waited for too long for file to download: ${actionFile.url}")
        installListenerIfNeeded(actionFile, workflowFile)
        break
      }

      Thread.sleep(50)
    }
  }

  override fun retryAllFailedActions() {
    retryFailedFetches.incModificationCount()
  }

  private fun getYamlActionFromFile(project: Project, actionFile: VirtualFile): YAMLFile? {
    return project.service<PsiManager>().findFile(actionFile) as? YAMLFile
  }

  @Synchronized
  private fun installListenerIfNeeded(actionFile: HttpVirtualFile, workflowFile: PsiFile) {
    val fileInfo = actionFile.fileInfo ?: return
    val virtualFileToWatch = workflowFile.virtualFile ?: return

    var listener = actionFile.getUserData(LISTENER_ATTACHED)
    if (listener == null) {
      listener = DownloadListener()
      fileInfo.addDownloadingListener(listener)
      Disposer.register(this) { fileInfo.removeDownloadingListener(listener) }
      actionFile.putUserData(LISTENER_ATTACHED, listener)
    }
    listener.addFile(virtualFileToWatch)
  }

  private fun retrieveFileForUses(description: ActionDescription): VirtualFile? {
    val url = getUrlFromUses(description) ?: return null
    return VirtualFileManager.getInstance().findFileByUrl(url)
  }

  private fun getUrlFromUses(uses: ActionDescription): String? {
    if (uses.isStandardAction()) {
      // "HEAD" is the latest commit on the default branch
      val ref = if (uses.isRefValid()) uses.ref else "HEAD"
      val path = if (uses.path != null) "${uses.path}/action.yml" else "action.yml"
      return "https://raw.githubusercontent.com/${uses.owner}/${uses.repo}/$ref/$path"
    }
    return null
  }

  override fun dispose() {
  }

  class DownloadListener : FileDownloadingListener {
    private val files = Collections.newSetFromMap(WeakHashMap<VirtualFile, Boolean>())

    override fun fileDownloaded(localFile: VirtualFile) {
      restartAnalyzers()
    }

    override fun errorOccurred(errorMessage: String) {
      restartAnalyzers()
    }

    override fun downloadingStarted() {
    }

    override fun downloadingCancelled() {
    }

    override fun progressMessageChanged(indeterminate: Boolean, message: String) {
    }

    override fun progressFractionChanged(fraction: Double) {
    }

    @Synchronized
    fun addFile(fileToRefresh: VirtualFile) {
      files.add(fileToRefresh)
    }

    @Synchronized
    private fun getAndClearFiles(): List<VirtualFile> {
      val result = ArrayList(files)
      files.clear()
      return result
    }

    /**
     * Restart any active daemon analyzer for the given file.
     */
    private fun restartAnalyzers() {
      val files = getAndClearFiles()
      if (files.isNotEmpty()) {
        invokeLater {
          FileContentUtilCore.reparseFiles(files)
        }
      }
    }
  }
}
