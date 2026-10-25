package vn.hongducdev.honortoolkit.feature.permissions

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

sealed interface PermissionsMessage {
    data object Changed : PermissionsMessage
    data class Blocked(val reason: BlockReason) : PermissionsMessage
    data class Failed(val output: String) : PermissionsMessage
    data object ScanFailed : PermissionsMessage
    data object Exported : PermissionsMessage
}

data class PermissionsUiState(
    val apps: List<AppItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = false,
    val selected: AppItem? = null,
    val detail: AppPermissions? = null,
    val permissionLabels: Map<String, String> = emptyMap(),
    val advancedOpen: Boolean = false,
    val message: PermissionsMessage? = null,
) {
    val visibleApps: List<AppItem>
        get() = apps.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
}

class PermissionsViewModel(application: Application) : AndroidViewModel(application) {
    private val packageManager: PackageManager = application.packageManager
    private val journal = PermissionsJournal(application)
    private val controller = PermissionsController(AdbSession.get(application), journal)

    private val mutableState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = mutableState.asStateFlow()

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
                it.copy(apps = apps ?: it.apps, loading = false, message = if (apps == null) PermissionsMessage.ScanFailed else it.message)
            }
        }
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun open(app: AppItem) {
        mutableState.update { it.copy(selected = app, detail = null, advancedOpen = false, loading = true) }
        loadDetail(app)
    }

    fun close() = mutableState.update { it.copy(selected = null, detail = null) }

    fun toggleAdvanced() = mutableState.update { it.copy(advancedOpen = !it.advancedOpen) }

    fun setGranted(permission: String, grant: Boolean) {
        val app = mutableState.value.selected ?: return
        apply(app) { controller.setGranted(app.packageName, permission, grant) }
    }

    fun setAppOp(op: String, mode: AppOpMode) {
        val app = mutableState.value.selected ?: return
        apply(app) { controller.setAppOp(app.packageName, op, mode) }
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    fun exportText(): String = journal.exportText()

    fun reportExported() = mutableState.update { it.copy(message = PermissionsMessage.Exported) }

    private fun apply(app: AppItem, change: suspend () -> PermissionOutcome) {
        viewModelScope.launch {
            val message = when (val outcome = change()) {
                PermissionOutcome.Done -> PermissionsMessage.Changed
                is PermissionOutcome.Blocked -> PermissionsMessage.Blocked(outcome.reason)
                is PermissionOutcome.Failed -> PermissionsMessage.Failed(outcome.output)
            }
            mutableState.update { it.copy(message = message) }
            loadDetail(app)
        }
    }

    private fun loadDetail(app: AppItem) {
        viewModelScope.launch {
            val detail = controller.load(app.packageName)
            val labels = detail?.let { d ->
                withContext(Dispatchers.IO) { d.permissions.associate { it.name to permissionLabel(it.name) } }
            }.orEmpty()
            mutableState.update {
                // The user may have gone back or opened another app while the shell was busy.
                if (it.selected != app) it
                else it.copy(
                    detail = detail,
                    permissionLabels = labels,
                    loading = false,
                    message = if (detail == null) PermissionsMessage.ScanFailed else it.message,
                )
            }
        }
    }

    private fun appLabel(packageName: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    private fun permissionLabel(name: String): String = try {
        packageManager.getPermissionInfo(name, 0).loadLabel(packageManager).toString()
            .replaceFirstChar { it.uppercase() }
    } catch (_: PackageManager.NameNotFoundException) {
        name.substringAfterLast('.')
    }
}
