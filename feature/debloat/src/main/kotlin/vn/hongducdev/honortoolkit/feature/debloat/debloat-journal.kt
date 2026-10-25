package vn.hongducdev.honortoolkit.feature.debloat

import android.content.Context
import java.io.File

enum class JournalAction { Disable, Enable }

data class JournalEntry(val timestampMillis: Long, val action: JournalAction, val packageName: String)

internal class DebloatJournal(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun append(action: JournalAction, packageName: String) {
        file.appendText("${System.currentTimeMillis()}\t${action.name}\t$packageName\n")
    }

    @Synchronized
    fun entries(): List<JournalEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val parts = line.split('\t')
            val millis = parts.getOrNull(0)?.toLongOrNull()
            val action = JournalAction.entries.firstOrNull { it.name == parts.getOrNull(1) }
            val name = parts.getOrNull(2)?.takeIf(::isValidPackageName)
            if (millis != null && action != null && name != null) JournalEntry(millis, action, name) else null
        }
    }

    /** Packages whose last recorded action is a disable, i.e. the ones this app still holds off. */
    fun currentlyDisabled(): Set<String> = entries()
        .groupBy { it.packageName }
        .filterValues { it.last().action == JournalAction.Disable }
        .keys

    /** Plain-text report with PC commands so recovery works even after the app is uninstalled. */
    fun exportText(): String = buildString {
        appendLine("Honor Toolkit - nhat ky debloat")
        appendLine()
        appendLine("Khoi phuc tu may tinh (bat Debug USB hoac Wireless debugging):")
        val disabled = currentlyDisabled()
        if (disabled.isEmpty()) appendLine("(khong co goi nao dang bi tat boi app)")
        disabled.sorted().forEach { appendLine("adb shell pm enable --user 0 $it") }
        appendLine()
        appendLine("Lich su:")
        entries().forEach { appendLine("${it.timestampMillis}\t${it.action.name}\t${it.packageName}") }
    }

    private companion object {
        const val FILE_NAME = "debloat-journal.tsv"
    }
}
