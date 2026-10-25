package vn.hongducdev.honortoolkit.core.adb

sealed interface AdbState {
    data object Disconnected : AdbState
    data object Connecting : AdbState
    data object NeedsPairing : AdbState
    data object Connected : AdbState
    data class Failed(val message: String) : AdbState
}

data class ShellResult(val exitCode: Int, val output: String) {
    val isSuccess: Boolean get() = exitCode == 0
}

interface AdbShell {
    suspend fun run(command: String): ShellResult
}
