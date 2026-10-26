package vn.hongducdev.honortoolkit.feature.quickshare

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.quickshare.connection.OutboundConnection
import vn.hongducdev.honortoolkit.core.quickshare.connection.OutboundListener
import vn.hongducdev.honortoolkit.core.quickshare.connection.OutgoingFile
import vn.hongducdev.honortoolkit.core.quickshare.connection.TransferOutcome
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

internal enum class SendPhase { CHOOSING, CONNECTING, AWAITING_ANSWER, SENDING, DONE, REJECTED, CANCELLED, FAILED }

internal data class SendUiState(
    val devices: List<NearbyDevice> = emptyList(),
    val phase: SendPhase = SendPhase.CHOOSING,
    val pin: String? = null,
    val percent: Int = 0,
    val fileCount: Int = 0,
)

internal class QuickShareSendViewModel(application: Application) : AndroidViewModel(application) {
    private val log = AdbSession.get(application).diagnostics
    private val browser = MdnsBrowser(application, log)
    private val mutableState = MutableStateFlow(SendUiState())
    val state: StateFlow<SendUiState> = mutableState.asStateFlow()
    private var files: List<OutgoingFile> = emptyList()
    private var started = false

    @Volatile
    private var connection: OutboundConnection? = null

    fun start(uris: List<Uri>) {
        if (started) return
        started = true
        val resolver = getApplication<Application>().contentResolver
        files = uris.mapNotNull { describe(resolver, it) }
        mutableState.update { it.copy(fileCount = files.size) }
        if (files.isEmpty()) {
            mutableState.update { it.copy(phase = SendPhase.FAILED) }
            return
        }
        browser.start(
            onFound = { found ->
                val own = QuickShareReceiverState.status.value.serviceName
                if (found.serviceName != own) {
                    mutableState.update { s -> s.copy(devices = s.devices.filterNot { it.serviceName == found.serviceName } + found) }
                }
            },
            onLost = { name -> mutableState.update { s -> s.copy(devices = s.devices.filterNot { it.serviceName == name }) } },
        )
    }

    fun send(device: NearbyDevice) {
        if (mutableState.value.phase != SendPhase.CHOOSING) return
        browser.stop()
        mutableState.update { it.copy(phase = SendPhase.CONNECTING) }
        thread(name = "quick-share-send") {
            val outcome = try {
                val socket = Socket()
                socket.connect(InetSocketAddress(device.address, device.port), CONNECT_TIMEOUT_MS)
                OutboundConnection(socket, localName(), files, Progress()).also { connection = it }.run()
            } catch (e: IOException) {
                TransferOutcome.Failed(e)
            }
            connection = null
            mutableState.update { it.copy(phase = phaseFor(outcome)) }
        }
    }

    fun cancel() {
        connection?.cancel()
    }

    override fun onCleared() {
        browser.stop()
        connection?.cancel()
    }

    private inner class Progress : OutboundListener {
        override fun onPin(pin: String) = mutableState.update { it.copy(pin = pin, phase = SendPhase.AWAITING_ANSWER) }

        override fun onAccepted() = mutableState.update { it.copy(phase = SendPhase.SENDING) }

        override fun onProgress(bytesSent: Long, bytesTotal: Long) {
            val percent = if (bytesTotal > 0) (bytesSent * 100 / bytesTotal).toInt() else 0
            mutableState.update { it.copy(percent = percent) }
        }
    }

    private fun phaseFor(outcome: TransferOutcome): SendPhase = when (outcome) {
        TransferOutcome.Completed -> SendPhase.DONE
        is TransferOutcome.Rejected -> SendPhase.REJECTED
        is TransferOutcome.Cancelled -> SendPhase.CANCELLED
        is TransferOutcome.Failed -> {
            log.record("quick share: send failed", outcome.cause)
            SendPhase.FAILED
        }
    }

    private fun localName(): String =
        Settings.Global.getString(getApplication<Application>().contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private fun describe(resolver: ContentResolver, uri: Uri): OutgoingFile? {
        try {
            var name = uri.lastPathSegment ?: "file"
            var size = -1L
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameColumn >= 0) name = cursor.getString(nameColumn) ?: name
                    val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn)
                }
            }
            if (size < 0) size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
            if (size < 0) return null
            val mime = resolver.getType(uri) ?: "application/octet-stream"
            return OutgoingFile(name, size, mime) { resolver.openInputStream(uri) ?: throw IOException("Cannot read $uri") }
        } catch (e: Exception) {
            log.record("quick share: cannot read shared file", e)
            return null
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
    }
}
