package vn.hongducdev.honortoolkit.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.core.adb.PairingForegroundService
import vn.hongducdev.honortoolkit.core.adb.ShellResult

data class HomeUiState(
    val adb: AdbState = AdbState.Disconnected,
    val pairingPort: Int? = null,
    val probe: ShellResult? = null,
    val busy: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val session = AdbSession.get(application)
    private val probe = MutableStateFlow<ShellResult?>(null)
    private val busy = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(session.state, session.pairingPort, probe, busy) { adb, port, result, isBusy ->
        HomeUiState(adb, port, result, isBusy)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), HomeUiState())

    init {
        reconnect()
    }

    fun reconnect() = launchBusy { session.connect() }

    fun startPairing() = PairingForegroundService.start(getApplication())

    fun submitCode(code: String) {
        val port = session.pairingPort.value ?: return
        launchBusy { session.pairAndConnect(port, code) }
    }

    fun runProbe() = launchBusy { probe.value = session.run("id") }

    private fun launchBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } finally {
                busy.value = false
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
