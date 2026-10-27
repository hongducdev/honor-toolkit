package vn.hongducdev.honortoolkit.feature.gmscheck

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.adb.AdbSession

data class GmsCheckUiState(val report: GmsReport? = null, val loading: Boolean = false, val failed: Boolean = false)

class GmsCheckViewModel(application: Application) : AndroidViewModel(application) {
    private val controller = GmsCheckController(AdbSession.get(application))

    private val mutableState = MutableStateFlow(GmsCheckUiState())
    val uiState: StateFlow<GmsCheckUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.update { it.copy(loading = true) }
            val report = controller.check()
            mutableState.update { GmsCheckUiState(report = report ?: it.report, loading = false, failed = report == null) }
        }
    }
}
