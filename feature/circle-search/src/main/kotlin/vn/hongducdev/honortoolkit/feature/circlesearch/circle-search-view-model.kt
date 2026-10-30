package vn.hongducdev.honortoolkit.feature.circlesearch

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState

enum class Readiness { NoGoogle, NotAssistant, Ready }

sealed interface CircleSearchMessage {
    data object AssistantSet : CircleSearchMessage
    data object AssistantRestored : CircleSearchMessage
    data object NothingToRestore : CircleSearchMessage
    data object NeedsAdb : CircleSearchMessage
    data class AssistantFailed(val reason: String) : CircleSearchMessage
}

data class CircleSearchUiState(
    val readiness: Readiness = Readiness.NotAssistant,
    val canRestore: Boolean = false,
    val busy: Boolean = false,
    val delayMs: Long = 0,
    val tileDelayMs: Long = 0,
    val vibrate: Boolean = true,
    val launcherEntries: Set<LauncherEntry> = emptySet(),
    val lastResult: String = "",
    val diagnostics: String = "",
    val stripEnabled: Boolean = false,
    val overlayGranted: Boolean = false,
    val notificationsGranted: Boolean = false,
    val stripWidthPercent: Long = 0,
    val stripHeightDp: Long = 0,
    val stripOffsetDp: Long = 0,
    val stripVisible: Boolean = false,
    val message: CircleSearchMessage? = null,
)

class CircleSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val config = TriggerConfig(application)
    private val session = AdbSession.get(application)
    private val assistant = AssistantSetup(application, session)
    private val _state = MutableStateFlow(snapshot())
    val uiState: StateFlow<CircleSearchUiState> = _state.asStateFlow()

    /** Re-reads everything the screen shows; call on resume since permissions change outside the app. */
    fun refresh() {
        _state.update { snapshot(it.message, it.busy) }
        NavBarStripService.sync(getApplication())
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun setDelay(value: Long) = change { config.delayMs = value }
    fun setTileDelay(value: Long) = change { config.tileDelayMs = value }
    fun setVibrate(value: Boolean) = change { config.vibrate = value }
    fun setLauncherEntry(entry: LauncherEntry, value: Boolean) = change { LauncherAlias.setEnabled(getApplication(), entry, value) }
    fun setStripEnabled(value: Boolean) = change { config.stripEnabled = value }
    fun setStripWidth(value: Long) = change { config.stripWidthPercent = value }
    fun setStripHeight(value: Long) = change { config.stripHeightDp = value }
    fun setStripOffset(value: Long) = change { config.stripOffsetDp = value }
    fun setStripVisible(value: Boolean) = change { config.stripVisible = value }

    fun test() {
        config.lastResult = ""
        TriggerRunner.run(getApplication(), TEST_DELAY_MS, TriggerSource.Test)
        viewModelScope.launch {
            delay(TEST_DELAY_MS + LOG_REFRESH_MS)
            refresh()
        }
    }

    fun clearLog() = change { TriggerLog.clear(getApplication()) }

    fun setGoogleAsAssistant() = launchAssistant {
        if (session.state.value != AdbState.Connected) return@launchAssistant CircleSearchMessage.NeedsAdb
        when (val result = assistant.setGoogle()) {
            AssistantResult.Done -> CircleSearchMessage.AssistantSet
            is AssistantResult.Failed -> CircleSearchMessage.AssistantFailed(result.reason)
        }
    }

    fun restoreAssistant() = launchAssistant {
        if (session.state.value != AdbState.Connected) return@launchAssistant CircleSearchMessage.NeedsAdb
        when (val result = assistant.restore()) {
            null -> CircleSearchMessage.NothingToRestore
            AssistantResult.Done -> CircleSearchMessage.AssistantRestored
            is AssistantResult.Failed -> CircleSearchMessage.AssistantFailed(result.reason)
        }
    }

    private fun change(block: () -> Unit) {
        block()
        refresh()
    }

    private fun launchAssistant(block: suspend () -> CircleSearchMessage) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val message = try {
                block()
            } catch (error: Exception) {
                CircleSearchMessage.AssistantFailed(error.javaClass.simpleName)
            }
            _state.value = snapshot(message, busy = false)
        }
    }

    private fun snapshot(message: CircleSearchMessage? = null, busy: Boolean = false): CircleSearchUiState {
        val app = getApplication<Application>()
        return CircleSearchUiState(
            readiness = readiness(app),
            canRestore = assistant.hasPrevious,
            busy = busy,
            delayMs = config.delayMs,
            tileDelayMs = config.tileDelayMs,
            vibrate = config.vibrate,
            launcherEntries = LauncherAlias.enabledEntries(app),
            lastResult = config.lastResult,
            diagnostics = TriggerLog.text(app),
            stripEnabled = config.stripEnabled,
            overlayGranted = Settings.canDrawOverlays(app),
            notificationsGranted = notificationsGranted(app),
            stripWidthPercent = config.stripWidthPercent,
            stripHeightDp = config.stripHeightDp,
            stripOffsetDp = config.stripOffsetDp,
            stripVisible = config.stripVisible,
            message = message,
        )
    }

    private fun readiness(app: Application): Readiness {
        val installed = runCatching { app.packageManager.getPackageInfo(AssistantSetup.GOOGLE_APP, 0) }.isSuccess
        val current = Settings.Secure.getString(app.contentResolver, "assistant").orEmpty()
        return when {
            !installed -> Readiness.NoGoogle
            !current.startsWith(AssistantSetup.GOOGLE_APP) -> Readiness.NotAssistant
            else -> Readiness.Ready
        }
    }

    private fun notificationsGranted(app: Application): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TEST_DELAY_MS = 2000L
        const val LOG_REFRESH_MS = 1500L
    }
}
