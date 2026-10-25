package vn.hongducdev.honortoolkit.feature.notificationfix

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.hongducdev.honortoolkit.core.adb.AdbSession

data class AppItem(val packageName: String, val label: String)

enum class FixMessage { Applied, PartiallyApplied, ShellFailed }

data class NotificationFixUiState(
    val apps: List<AppItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val selected: AppItem? = null,
    val steps: List<StepState>? = null,
    val message: FixMessage? = null,
) {
    val visibleApps: List<AppItem>
        get() = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }

    val canApply: Boolean get() = !loading && steps?.any { it.status == StepStatus.Missing || it.status == StepStatus.Failed } == true
}

class NotificationFixViewModel(application: Application) : AndroidViewModel(application) {
    private val packageManager: PackageManager = application.packageManager
    private val controller = NotificationFixController(AdbSession.get(application))

    private val mutableState = MutableStateFlow(NotificationFixUiState())
    val uiState: StateFlow<NotificationFixUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val packages = controller.userPackages()
            val apps = packages?.let { names ->
                withContext(Dispatchers.IO) { names.map { AppItem(it, appLabel(it)) }.sortedBy { it.label.lowercase() } }
            }
            mutableState.update {
                it.copy(apps = apps ?: it.apps, loading = false, message = if (apps == null) FixMessage.ShellFailed else it.message)
            }
        }
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun open(app: AppItem) {
        mutableState.update { it.copy(selected = app, steps = null, loading = true) }
        viewModelScope.launch {
            val steps = controller.inspect(app.packageName)
            mutableState.update {
                // The user may have gone back or opened another app while the shell was busy.
                if (it.selected != app) it
                else it.copy(steps = steps, loading = false, message = if (steps == null) FixMessage.ShellFailed else it.message)
            }
        }
    }

    fun close() = mutableState.update { it.copy(selected = null, steps = null) }

    fun applyFix() {
        val app = mutableState.value.selected ?: return
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val steps = controller.apply(app.packageName)
            mutableState.update {
                if (it.selected != app) it
                else it.copy(
                    steps = steps ?: it.steps,
                    loading = false,
                    message = when {
                        steps == null -> FixMessage.ShellFailed
                        steps.any { s -> s.status == StepStatus.Failed } -> FixMessage.PartiallyApplied
                        else -> FixMessage.Applied
                    },
                )
            }
        }
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }
}
