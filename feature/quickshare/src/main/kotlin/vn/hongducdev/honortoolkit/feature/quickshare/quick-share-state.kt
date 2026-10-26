package vn.hongducdev.honortoolkit.feature.quickshare

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal enum class LastResult { NONE, DONE, REJECTED, CANCELLED, FAILED }

internal class PendingOffer(val id: Int, val senderName: String, val pin: String, val fileCount: Int)

internal data class ReceiverStatus(
    val running: Boolean = false,
    val deviceName: String = "",
    val progressPercent: Int? = null,
    val last: LastResult = LastResult.NONE,
    val offer: PendingOffer? = null,
    /** Our own mDNS instance name, so the send list can leave this phone out. */
    val serviceName: String? = null,
)

/** Shared between the service (writer) and the screen (reader); the service is the only process component. */
internal object QuickShareReceiverState {
    private val mutable = MutableStateFlow(ReceiverStatus())
    val status: StateFlow<ReceiverStatus> = mutable.asStateFlow()

    fun update(transform: (ReceiverStatus) -> ReceiverStatus) = mutable.update(transform)
}
