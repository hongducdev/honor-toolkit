package vn.hongducdev.honortoolkit.feature.permissions

import android.content.Context
import java.io.File

enum class ChangeKind { Permission, AppOp }

data class JournalEntry(
    val timestampMillis: Long,
    val kind: ChangeKind,
    val packageName: String,
    val name: String,
    val before: String,
    val after: String,
)

internal class PermissionsJournal(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun append(kind: ChangeKind, packageName: String, name: String, before: String, after: String) {
        file.appendText("${System.currentTimeMillis()}\t${kind.name}\t$packageName\t$name\t$before\t$after\n")
    }

    @Synchronized
    fun entries(): List<JournalEntry> {
        if (!file.exists()) return emptyList()
        return file.readLines().mapNotNull { line ->
            val p = line.split('\t')
            val millis = p.getOrNull(0)?.toLongOrNull()
            val kind = ChangeKind.entries.firstOrNull { it.name == p.getOrNull(1) }
            if (millis == null || kind == null || p.size < 6) null else JournalEntry(millis, kind, p[2], p[3], p[4], p[5])
        }
    }

    /** Plain-text report with PC commands that put each changed item back to its previous value. */
    fun exportText(): String = buildString {
        val all = entries()
        appendLine("Honor Toolkit - nhat ky quyen")
        appendLine()
        appendLine("Hoan tac tu may tinh (bat Debug USB hoac Wireless debugging), tu moi nhat den cu nhat:")
        all.asReversed().forEach { e ->
            if (!isValidPackageName(e.packageName)) return@forEach
            when (e.kind) {
                ChangeKind.Permission ->
                    if (isValidPackageName(e.name)) {
                        appendLine("adb shell pm ${if (e.before == "granted") "grant" else "revoke"} --user 0 ${e.packageName} ${e.name}")
                    }
                ChangeKind.AppOp ->
                    if (isValidAppOpName(e.name) && AppOpMode.entries.any { it.argument == e.before }) {
                        appendLine("adb shell cmd appops set --user 0 ${e.packageName} ${e.name} ${e.before}")
                    }
            }
        }
        appendLine()
        appendLine("Lich su:")
        all.forEach { appendLine("${it.timestampMillis}\t${it.kind.name}\t${it.packageName}\t${it.name}\t${it.before}\t${it.after}") }
    }

    private companion object {
        const val FILE_NAME = "permissions-journal.tsv"
    }
}
