package vn.hongducdev.honortoolkit.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vn.hongducdev.honortoolkit.core.adb.AdbSession

class DeviceUsageViewModel(application: Application) : AndroidViewModel(application) {
    private val session = AdbSession.get(application)
    private val mutableState = MutableStateFlow<UsageLoad?>(null)

    /** Null while loading. */
    val state: StateFlow<UsageLoad?> = mutableState

    init {
        refresh()
    }

    fun refresh() {
        mutableState.value = null
        viewModelScope.launch {
            mutableState.value = withContext(Dispatchers.Default) { loadAppUsage(getApplication(), session) }
        }
    }
}
