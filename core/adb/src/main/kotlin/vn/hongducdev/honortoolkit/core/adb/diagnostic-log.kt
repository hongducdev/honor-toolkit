package vn.hongducdev.honortoolkit.core.adb

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MagicOS hides third-party Info/Debug logcat lines, so failures are also written to a file
 * that can be exported with `adb shell run-as <pkg> cat files/diagnostics.log`.
 */
class DiagnosticLog internal constructor(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val timestamp = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun record(message: String, error: Throwable? = null) {
        Log.e(TAG, message, error)
        if (file.length() > MAX_BYTES) file.writeText("")
        val suffix = error?.let { " (${it::class.java.simpleName}: ${it.message})" }.orEmpty()
        file.appendText("${timestamp.format(Date())} $message$suffix\n")
    }

    @Synchronized
    fun read(): String = if (file.exists()) file.readText() else ""

    private companion object {
        const val TAG = "HonorToolkit"
        const val FILE_NAME = "diagnostics.log"
        const val MAX_BYTES = 64 * 1024L
    }
}
