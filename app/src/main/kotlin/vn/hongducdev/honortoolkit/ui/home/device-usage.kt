package vn.hongducdev.honortoolkit.ui.home

import android.content.Context
import android.content.pm.PackageManager
import vn.hongducdev.honortoolkit.core.adb.AdbShell

data class AppUsage(
    val packageName: String,
    val label: String,
    val ramBytes: Long,
    val storageBytes: Long,
)

sealed interface UsageLoad {
    data class Loaded(val apps: List<AppUsage>) : UsageLoad
    data class Failed(val message: String) : UsageLoad
}

private val PROCESS_LINE = Regex("""^\s*([\d,]+)K: (\S+) \(pid \d+""")
private val QUOTED = Regex(""""([^"]+)"""")
private const val KIB = 1024L

/** Per-package memory in bytes, summing every process of a package. Prefers the PSS table and falls back to RSS. */
internal fun parseMeminfo(output: String): Map<String, Long> {
    val lines = output.lines()
    val start = listOf("Total PSS by process:", "Total RSS by process:")
        .map { header -> lines.indexOfFirst { it.trim() == header } }
        .firstOrNull { it >= 0 } ?: return emptyMap()
    val totals = mutableMapOf<String, Long>()
    for (line in lines.drop(start + 1)) {
        if (line.isBlank()) break
        val match = PROCESS_LINE.find(line) ?: continue
        val kib = match.groupValues[1].replace(",", "").toLongOrNull() ?: continue
        val pkg = match.groupValues[2].substringBefore(':')
        totals.merge(pkg, kib * KIB, Long::plus)
    }
    return totals
}

/** Per-package app + data + cache bytes from `dumpsys diskstats`, whose arrays are index-aligned with the package list. */
internal fun parseDiskstats(output: String): Map<String, Long> {
    val names = QUOTED.findAll(lineAfter(output, "Package Names:")).map { it.groupValues[1] }.toList()
    if (names.isEmpty()) return emptyMap()
    val app = numbers(lineAfter(output, "App Sizes:"))
    val data = numbers(lineAfter(output, "App Data Sizes:"))
    val cache = numbers(lineAfter(output, "Cache Sizes:"))
    return names.withIndex().associate { (i, name) ->
        name to app.getOrElse(i) { 0L } + data.getOrElse(i) { 0L } + cache.getOrElse(i) { 0L }
    }
}

private fun lineAfter(output: String, prefix: String): String =
    output.lineSequence().firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix).orEmpty()

private fun numbers(array: String): List<Long> =
    array.trim().removePrefix("[").removeSuffix("]").split(',').mapNotNull { it.trim().toLongOrNull() }

internal fun mergeUsage(ram: Map<String, Long>, storage: Map<String, Long>, labelOf: (String) -> String): List<AppUsage> =
    (ram.keys + storage.keys).map { pkg ->
        AppUsage(pkg, labelOf(pkg), ram[pkg] ?: 0L, storage[pkg] ?: 0L)
    }.filter { it.ramBytes > 0 || it.storageBytes > 0 }

suspend fun loadAppUsage(context: Context, shell: AdbShell): UsageLoad {
    val memory = shell.run("dumpsys meminfo")
    if (!memory.isSuccess) return UsageLoad.Failed(memory.output)
    val disk = shell.run("dumpsys diskstats")
    if (!disk.isSuccess) return UsageLoad.Failed(disk.output)
    val pm = context.packageManager
    val label = { pkg: String ->
        try {
            pm.getApplicationInfo(pkg, 0).loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            pkg
        }
    }
    return UsageLoad.Loaded(mergeUsage(parseMeminfo(memory.output), parseDiskstats(disk.output), label))
}
