package vn.hongducdev.honortoolkit.feature.automation

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AutomationMessage {
    data object Saved : AutomationMessage
    data object Deleted : AutomationMessage
    data class Invalid(val text: String) : AutomationMessage
    data class Undone(val count: Int) : AutomationMessage
    data object UndoEmpty : AutomationMessage
    data class UndoFailed(val failed: Int) : AutomationMessage
    data object MissingPermission : AutomationMessage
}

data class AutomationUiState(
    val rules: List<AutomationRule> = emptyList(),
    val log: String = "",
    val editor: AutomationRule? = null,
    val missingByRule: Map<String, Set<AutomationRequirement>> = emptyMap(),
    val message: AutomationMessage? = null,
) {
    val missing: Set<AutomationRequirement> get() = missingByRule.values.flatten().toSet()

    /** The switch and the engine both use this: a rule without its permissions does not run. */
    fun isLive(rule: AutomationRule): Boolean = rule.enabled && missingByRule[rule.id].orEmpty().isEmpty()
}

class AutomationViewModel(application: Application) : AndroidViewModel(application) {
    private val store = AutomationStorage.store(application)
    private val mutableState = MutableStateFlow(AutomationUiState())
    val uiState: StateFlow<AutomationUiState> = mutableState.asStateFlow()

    init {
        reload()
    }

    fun refresh() = reload()

    fun startNew() = mutableState.update {
        it.copy(
            editor = AutomationRule(
                id = UUID.randomUUID().toString(),
                name = "",
                enabled = true,
                trigger = Trigger.Charger(plugged = true),
                actions = emptyList(),
            ),
        )
    }

    fun edit(rule: AutomationRule) = mutableState.update { it.copy(editor = rule) }

    fun closeEditor() = mutableState.update { it.copy(editor = null) }

    fun save(rule: AutomationRule) {
        val problems = rule.problems()
        if (problems.isNotEmpty()) {
            mutableState.update { it.copy(message = AutomationMessage.Invalid(problems.joinToString("; "))) }
            return
        }
        store.upsert(rule)
        syncRuntime()
        mutableState.update { it.copy(editor = null, message = AutomationMessage.Saved) }
        reload()
    }

    fun toggle(rule: AutomationRule, enabled: Boolean) {
        if (enabled && mutableState.value.missingByRule[rule.id].orEmpty().isNotEmpty()) {
            mutableState.update { it.copy(message = AutomationMessage.MissingPermission) }
            return
        }
        store.setEnabled(rule.id, enabled)
        syncRuntime()
        reload()
    }

    fun delete(rule: AutomationRule) {
        store.remove(rule.id)
        syncRuntime()
        mutableState.update { it.copy(editor = null, message = AutomationMessage.Deleted) }
        reload()
    }

    fun clearLog() {
        store.clearLog()
        reload()
    }

    fun undoLastWrite() {
        val entries = store.undoEntries()
        if (entries.isEmpty()) {
            mutableState.update { it.copy(message = AutomationMessage.UndoEmpty) }
            return
        }
        viewModelScope.launch {
            val failed = entries.count { snapshot ->
                val restored = if (snapshot.value == null) {
                    SettingsAccess.delete(getApplication(), snapshot.scope, snapshot.key)
                } else {
                    SettingsAccess.write(getApplication(), snapshot.scope, snapshot.key, snapshot.value)
                }
                !restored
            }
            store.clearUndo()
            mutableState.update {
                it.copy(
                    message = if (failed == 0) {
                        AutomationMessage.Undone(entries.size)
                    } else {
                        AutomationMessage.UndoFailed(failed)
                    },
                )
            }
            reload()
        }
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }

    /** Starts or stops the service and re-registers alarms and proximity alerts. */
    private fun syncRuntime() {
        val context = getApplication<Application>()
        AutomationForegroundService.sync(context)
    }

    private fun reload() {
        val rules = store.rules()
        mutableState.update {
            it.copy(
                rules = rules,
                log = store.log(),
                missingByRule = rules.associate { rule ->
                    rule.id to AutomationPermissions.missing(getApplication(), rule)
                },
            )
        }
    }
}
