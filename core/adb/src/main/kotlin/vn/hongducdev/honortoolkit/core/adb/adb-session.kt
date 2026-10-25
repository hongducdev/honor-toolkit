package vn.hongducdev.honortoolkit.core.adb

import android.content.Context
import android.content.pm.PackageManager
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Talks to the phone's own Wireless debugging daemon. The connection is re-established on demand
 * because MagicOS drops pairing and changes the port after every reboot.
 */
class AdbSession private constructor(context: Context) : AdbShell {
    private val appContext = context.applicationContext
    private val manager = HonorAdbConnectionManager(AdbKeyStore(appContext))
    val diagnostics = DiagnosticLog(appContext)
    private val executor = Executors.newCachedThreadPool { task -> Thread(task, "adb-command").apply { isDaemon = true } }
    private val gate = Mutex()

    private val mutableState = MutableStateFlow<AdbState>(AdbState.Disconnected)
    val state: StateFlow<AdbState> = mutableState.asStateFlow()

    private val mutablePairingPort = MutableStateFlow<Int?>(null)
    val pairingPort: StateFlow<Int?> = mutablePairingPort.asStateFlow()

    internal fun publishPairingPort(port: Int?) {
        mutablePairingPort.value = port
    }

    suspend fun connect(): AdbState = gate.withLock { connectLocked() }

    suspend fun pairAndConnect(port: Int, code: String): AdbState = gate.withLock {
        val paired = withContext(Dispatchers.IO) {
            try {
                manager.pair(PAIRING_HOST, port, code)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.record("pair failed", e)
                false
            }
        }
        if (!paired) {
            diagnostics.record("pair rejected on port $port")
            AdbState.Failed("Ghép cặp thất bại. Kiểm tra lại mã và giữ hộp thoại mã mở").also { mutableState.value = it }
        } else {
            publishPairingPort(null)
            connectLocked()
        }
    }

    override suspend fun run(command: String): ShellResult = gate.withLock {
        withContext(Dispatchers.IO) {
            if (!manager.isConnected && connectLocked() != AdbState.Connected) {
                return@withContext ShellResult(NOT_CONNECTED, "Chưa kết nối ADB")
            }
            try {
                execWithDeadline(command)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                diagnostics.record("run failed: $command", e)
                dropConnection()
                ShellResult(NOT_CONNECTED, e.message ?: e::class.java.simpleName)
            }
        }
    }

    private suspend fun connectLocked(): AdbState = withContext(Dispatchers.IO) {
        mutableState.value = AdbState.Connecting
        val result = try {
            if (manager.connectTls(appContext, CONNECT_TIMEOUT_MS)) {
                if (grantSecureSettings()) AdbState.Connected else AdbState.Failed("Mất kết nối khi cấp quyền")
            } else {
                AdbState.Failed("Không tìm thấy dịch vụ Gỡ lỗi qua WLAN")
            }
        } catch (e: AdbPairingRequiredException) {
            AdbState.NeedsPairing
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics.record("connect failed", e)
            AdbState.Failed(e.message ?: e::class.java.simpleName)
        }
        mutableState.value = result
        result
    }

    private fun dropConnection() {
        manager.disconnect()
        mutableState.value = AdbState.Disconnected
    }

    /**
     * libadb's stream read ignores both interruption and disconnect, so the command runs on its own
     * thread and is abandoned when the deadline passes.
     */
    private suspend fun execWithDeadline(command: String): ShellResult {
        val received = StringBuffer()
        val pending = executor.submit<ShellResult> { exec(command, received) }
        return try {
            pending.get(RUN_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            pending.cancel(true)
            diagnostics.record("run timed out after ${RUN_TIMEOUT_MS}ms with ${received.length} chars received: $command")
            dropConnection()
            ShellResult(NOT_CONNECTED, "Hết thời gian chờ lệnh")
        } catch (e: ExecutionException) {
            throw e.cause ?: e
        }
    }

    /** Stops reading at the exit marker instead of waiting for the stream to close, which adbd does not always do. */
    private fun exec(command: String, received: StringBuffer): ShellResult {
        manager.openStream("shell:$command\necho $EXIT_MARKER\$?").use { stream ->
            val input = stream.openInputStream().bufferedReader()
            val chunk = CharArray(READ_CHUNK_CHARS)
            while (EXIT_PATTERN.find(received) == null) {
                val count = input.read(chunk)
                if (count < 0) break
                received.append(chunk, 0, count)
            }
        }
        val exit = EXIT_PATTERN.find(received) ?: return ShellResult(NOT_CONNECTED, received.toString().trim())
        val code = exit.groupValues[1].toIntOrNull() ?: NOT_CONNECTED
        return ShellResult(code, received.substring(0, exit.range.first).trim())
    }

    /** Returns false only when the connection itself failed; a refused grant is logged and tolerated. */
    private suspend fun grantSecureSettings(): Boolean {
        val granted = appContext.checkSelfPermission(SECURE_SETTINGS_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (granted) return true
        val result = execWithDeadline("pm grant ${appContext.packageName} $SECURE_SETTINGS_PERMISSION")
        if (!result.isSuccess) diagnostics.record("grant WRITE_SECURE_SETTINGS failed: ${result.output}")
        return result.exitCode != NOT_CONNECTED
    }

    companion object {
        private const val PAIRING_HOST = "127.0.0.1"
        private const val CONNECT_TIMEOUT_MS = 10_000L
        private const val RUN_TIMEOUT_MS = 30_000L
        private const val EXIT_MARKER = "__HONOR_TOOLKIT_EXIT__"
        private const val READ_CHUNK_CHARS = 1024
        private val EXIT_PATTERN = Regex("$EXIT_MARKER(\\d+)\\s")
        private const val NOT_CONNECTED = -1
        private const val SECURE_SETTINGS_PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

        @Volatile private var instance: AdbSession? = null

        fun get(context: Context): AdbSession =
            instance ?: synchronized(this) {
                instance ?: AdbSession(context).also { instance = it }
            }
    }
}
