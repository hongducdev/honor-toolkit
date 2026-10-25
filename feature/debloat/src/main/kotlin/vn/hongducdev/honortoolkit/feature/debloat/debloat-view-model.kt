package vn.hongducdev.honortoolkit.feature.debloat

import android.app.Application
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

enum class DebloatFilter { Enabled, Disabled }

sealed interface DebloatMessage {
    data class Disabled(val label: String) : DebloatMessage
    data class Enabled(val label: String) : DebloatMessage
    data class Blocked(val reason: ProtectionReason) : DebloatMessage
    data class Failed(val output: String) : DebloatMessage
    data object ScanFailed : DebloatMessage
    data object Exported : DebloatMessage
}

data class DebloatUiState(
    val entries: List<PackageEntry> = emptyList(),
    val query: String = "",
    val filter: DebloatFilter = DebloatFilter.Enabled,
    val loading: Boolean = false,
    val pendingDisable: PackageEntry? = null,
    val message: DebloatMessage? = null,
) {
    val visible: List<PackageEntry>
        get() = entries.filter { (it.enabled == (filter == DebloatFilter.Enabled)) && it.matches(query) }
}

private fun PackageEntry.matches(query: String): Boolean =
    query.isBlank() || label.contains(query, ignoreCase = true) || packageName.contains(query, ignoreCase = true)

class DebloatViewModel(application: Application) : AndroidViewModel(application) {
    private val session = AdbSession.get(application)
    private val scanner = PackageScanner(application, session)
    private val journal = DebloatJournal(application)
    private val controller = DebloatController(session, scanner, journal)

    private val mutableState = MutableStateFlow(DebloatUiState())
    val uiState: StateFlow<DebloatUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val scanned = scanner.scan()
            mutableState.update {
                it.copy(
                    entries = scanned ?: it.entries,
                    loading = false,
                    message = if (scanned == null) DebloatMessage.ScanFailed else it.message,
                )
            }
        }
    }

    fun setQuery(query: String) = mutableState.update { it.copy(query = query) }

    fun setFilter(filter: DebloatFilter) = mutableState.update { it.copy(filter = filter) }

    fun requestDisable(entry: PackageEntry) = mutableState.update { it.copy(pendingDisable = entry) }

    fun cancelDisable() = mutableState.update { it.copy(pendingDisable = null) }

    fun confirmDisable() {
        val entry = mutableState.value.pendingDisable ?: return
        mutableState.update { it.copy(pendingDisable = null) }
        apply(entry, { controller.disable(entry.packageName) },DebloatMessage.Disabled(entry.label))
    }

    fun enable(entry: PackageEntry) =
        apply(entry, { controller.enable(entry.packageName) }, DebloatMessage.Enabled(entry.label))

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    suspend fun exportText(): String = withContext(Dispatchers.IO) { journal.exportText() }

    fun reportExported() = mutableState.update { it.copy(message = DebloatMessage.Exported) }

    private fun apply(entry: PackageEntry, action: suspend () -> DebloatOutcome, success: DebloatMessage) {
        viewModelScope.launch {
            val message = when (val outcome = action()) {
                DebloatOutcome.Done -> success
                is DebloatOutcome.Blocked -> DebloatMessage.Blocked(outcome.reason)
                DebloatOutcome.InvalidName -> DebloatMessage.Failed(entry.packageName)
                is DebloatOutcome.Failed -> DebloatMessage.Failed(outcome.output)
            }
            mutableState.update { it.copy(message = message) }
            if (message === success) refresh()
        }
    }
}
