package vn.hongducdev.honortoolkit.feature.tune

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.adb.AdbSession

sealed interface TuneMessage {
    data class Applied(val on: Boolean) : TuneMessage
    data object Refused : TuneMessage
    data object ShellFailed : TuneMessage
    data object Unsupported : TuneMessage
    data class Preset(val applied: Int, val total: Int, val skipped: Int) : TuneMessage
    data class Restored(val restored: Int, val total: Int) : TuneMessage
    data object GpsSent : TuneMessage
    data object GpsFailed : TuneMessage
}

data class TuneUiState(
    val loading: Boolean = false,
    val toggles: Map<TuneId, Boolean?> = emptyMap(),
    val choices: Map<TuneId, String?> = emptyMap(),
    val message: TuneMessage? = null,
    val confirmRestore: Boolean = false,
    val confirmRecommended: Boolean = false,
) {
    fun isOn(id: TuneId): Boolean = toggles[id] == true
}

class TuneViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = TuneController(AdbSession.get(application))

    private val mutableState = MutableStateFlow(TuneUiState())
    val uiState: StateFlow<TuneUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val snapshot = controller.snapshot()
            mutableState.update {
                if (snapshot == null) it.copy(loading = false, message = TuneMessage.ShellFailed)
                else it.copy(loading = false, toggles = snapshot.toggles, choices = snapshot.choices)
            }
        }
    }

    fun setToggle(id: TuneId, enabled: Boolean) {
        if (mutableState.value.loading) return
        val spec = TuneCatalog.toggle(id)
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val outcome = controller.setToggle(spec, enabled)
            val snapshot = controller.snapshot()
            mutableState.update {
                if (snapshot == null) {
                    it.copy(loading = false, message = TuneMessage.ShellFailed)
                } else {
                    it.copy(
                        loading = false,
                        toggles = snapshot.toggles,
                        choices = snapshot.choices,
                        message = when (outcome) {
                            TuneOutcome.Applied -> TuneMessage.Applied(enabled)
                            TuneOutcome.Refused -> TuneMessage.Refused
                            TuneOutcome.ShellFailed -> TuneMessage.ShellFailed
                            TuneOutcome.Unsupported -> TuneMessage.Unsupported
                        },
                    )
                }
            }
        }
    }

    fun choose(id: TuneId, value: String) {
        if (mutableState.value.loading) return
        val spec = TuneCatalog.choice(id)
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val outcome = controller.choose(spec, value)
            val snapshot = controller.snapshot()
            mutableState.update {
                if (snapshot == null) {
                    it.copy(loading = false, message = TuneMessage.ShellFailed)
                } else {
                    it.copy(
                        loading = false,
                        toggles = snapshot.toggles,
                        choices = snapshot.choices,
                        message = when (outcome) {
                            TuneOutcome.Applied -> TuneMessage.Applied(true)
                            TuneOutcome.Refused -> TuneMessage.Refused
                            TuneOutcome.ShellFailed -> TuneMessage.ShellFailed
                            TuneOutcome.Unsupported -> TuneMessage.Unsupported
                        },
                    )
                }
            }
        }
    }

    fun requestRecommended() = mutableState.update { it.copy(confirmRecommended = true) }

    fun cancelRecommended() = mutableState.update { it.copy(confirmRecommended = false) }

    fun applyRecommended() {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(confirmRecommended = false, loading = true) }
        viewModelScope.launch {
            val outcome = controller.applyRecommended()
            val snapshot = controller.snapshot()
            mutableState.update {
                if (outcome == null || snapshot == null) {
                    it.copy(loading = false, message = TuneMessage.ShellFailed)
                } else {
                    it.copy(
                        loading = false,
                        toggles = snapshot.toggles,
                        choices = snapshot.choices,
                        message = TuneMessage.Preset(outcome.applied, outcome.total, outcome.skipped),
                    )
                }
            }
        }
    }

    fun requestRestore() = mutableState.update { it.copy(confirmRestore = true) }

    fun cancelRestore() = mutableState.update { it.copy(confirmRestore = false) }

    fun confirmRestore() {
        mutableState.update { it.copy(confirmRestore = false, loading = true) }
        viewModelScope.launch {
            val outcome = controller.restoreDefaults()
            val snapshot = controller.snapshot()
            mutableState.update {
                if (outcome == null || snapshot == null) {
                    it.copy(loading = false, message = TuneMessage.ShellFailed)
                } else {
                    it.copy(
                        loading = false,
                        toggles = snapshot.toggles,
                        choices = snapshot.choices,
                        message = TuneMessage.Restored(outcome.applied, outcome.total),
                    )
                }
            }
        }
    }

    fun resetGps() {
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val sent = controller.resetGps()
            mutableState.update {
                it.copy(loading = false, message = if (sent) TuneMessage.GpsSent else TuneMessage.GpsFailed)
            }
        }
    }

    fun consumeMessage() = mutableState.update { it.copy(message = null) }
}
