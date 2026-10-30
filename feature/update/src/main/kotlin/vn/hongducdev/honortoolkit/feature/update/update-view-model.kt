package vn.hongducdev.honortoolkit.feature.update

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UpdateUiState(
    val currentVersion: String,
    val checking: Boolean = false,
    val update: AvailableUpdate? = null,
    val skippedVersion: String? = null,
    val autoCheck: Boolean = true,
    val downloading: Boolean = false,
    val progress: Float = 0f,
    val apkPath: String? = null,
    val needsInstallPermission: Boolean = false,
    val error: String? = null,
    val upToDate: Boolean = false,
    val lastCheckedAt: Long = 0L,
) {
    /** The newer release to advertise, unless the user chose to skip that version. */
    val visibleUpdate: AvailableUpdate? get() = update?.takeIf { it.versionName != skippedVersion }
}

/**
 * One instance is shared by the Home banner and the update page (both use the activity's view model
 * store), so a check started from Home fills the page too. The first composition of Home triggers
 * [checkIfDue], which asks GitHub at most once per [CheckIntervalMs].
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = UpdateController(application)
    private val mutableState = MutableStateFlow(
        UpdateUiState(
            currentVersion = controller.currentVersionName,
            autoCheck = controller.autoCheck,
            skippedVersion = controller.skippedVersion,
            lastCheckedAt = controller.lastCheckedAt,
        ),
    )
    val uiState: StateFlow<UpdateUiState> = mutableState.asStateFlow()

    private var downloadJob: Job? = null

    init {
        checkIfDue()
    }

    fun checkIfDue() {
        val state = mutableState.value
        if (!state.autoCheck || state.checking || state.downloading) return
        if (System.currentTimeMillis() - controller.lastCheckedAt < CheckIntervalMs) return
        check()
    }

    fun check() {
        val state = mutableState.value
        if (state.checking || state.downloading) return
        mutableState.update { it.copy(checking = true, error = null, upToDate = false) }
        viewModelScope.launch {
            try {
                when (val result = withContext(Dispatchers.IO) { controller.checkNow() }) {
                    is CheckResult.Newer -> mutableState.update {
                        it.copy(
                            checking = false,
                            update = result.update,
                            upToDate = false,
                            lastCheckedAt = controller.lastCheckedAt,
                            // A fresh answer may point at another asset; install only what this check found.
                            apkPath = null,
                            progress = 0f,
                            needsInstallPermission = false,
                        )
                    }
                    CheckResult.UpToDate -> mutableState.update {
                        it.copy(checking = false, update = null, upToDate = true, lastCheckedAt = controller.lastCheckedAt)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LogTag, "Update check failed", e)
                mutableState.update { it.copy(checking = false, error = e.userMessage(), lastCheckedAt = controller.lastCheckedAt) }
            }
        }
    }

    fun download() {
        val update = mutableState.value.update ?: return
        if (mutableState.value.downloading) return
        mutableState.update { it.copy(downloading = true, progress = 0f, apkPath = null, error = null) }
        downloadJob = viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    controller.download(update) { progress -> mutableState.update { it.copy(progress = progress) } }
                }
                mutableState.update { it.copy(downloading = false, progress = 1f, apkPath = file.absolutePath) }
                install()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(LogTag, "Update download failed", e)
                mutableState.update { it.copy(downloading = false, progress = 0f, error = e.userMessage()) }
            }
        }
    }

    fun install() {
        val path = mutableState.value.apkPath ?: return
        try {
            when (controller.install(File(path))) {
                InstallResult.Started -> mutableState.update { it.copy(needsInstallPermission = false, error = null) }
                InstallResult.NeedsPermission -> mutableState.update { it.copy(needsInstallPermission = true, error = null) }
            }
        } catch (e: Exception) {
            Log.w(LogTag, "Installer launch failed", e)
            mutableState.update { it.copy(error = e.userMessage()) }
        }
    }

    /** Called when the update page returns to the foreground, i.e. after the user granted the installer permission. */
    fun onResumed() {
        val canInstall = controller.canInstall()
        mutableState.update {
            if (it.needsInstallPermission && canInstall) it.copy(needsInstallPermission = false) else it
        }
    }

    fun setAutoCheck(enabled: Boolean) {
        controller.autoCheck = enabled
        mutableState.update { it.copy(autoCheck = enabled) }
        if (enabled) checkIfDue()
    }

    fun skipCurrent() {
        val version = mutableState.value.update?.versionName ?: return
        controller.skippedVersion = version
        mutableState.update { it.copy(skippedVersion = version) }
    }

    fun clearSkip() {
        controller.skippedVersion = null
        mutableState.update { it.copy(skippedVersion = null) }
    }

    fun openReleasePage() {
        val url = mutableState.value.update?.pageUrl?.takeIf { it.isNotBlank() } ?: return
        runCatching { controller.openReleasePage(url) }
            .onFailure { mutableState.update { state -> state.copy(error = it.userMessage()) } }
    }

    fun clearError() {
        mutableState.update { it.copy(error = null) }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        mutableState.update { it.copy(downloading = false, progress = 0f) }
    }

    private fun Throwable.userMessage(): String = message?.takeIf { it.isNotBlank() } ?: "Không kiểm tra được bản cập nhật."

    private companion object {
        const val LogTag = "HonorToolkitUpdate"
        const val CheckIntervalMs = 6 * 60 * 60 * 1000L
    }
}
