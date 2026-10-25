package vn.hongducdev.honortoolkit.feature.permissions

data class PermissionEntry(val name: String, val granted: Boolean, val flags: Set<String>) {
    /** Fixed by the system or a policy: ADB cannot change these, so they are shown read-only. */
    val fixed: Boolean get() = "SYSTEM_FIXED" in flags || "POLICY_FIXED" in flags
}

data class PackageDump(val permissions: List<PermissionEntry>, val sharedUid: Boolean)

enum class AppOpMode(val argument: String) {
    Allow("allow"),
    Ignore("ignore"),
    Deny("deny"),
    Foreground("foreground"),
    Default("default"),
}

data class AppOpEntry(val name: String, val mode: AppOpMode)

private val RUNTIME_LINE = Regex("^([A-Za-z][A-Za-z0-9_.]*): granted=(true|false)(?:, flags=\\[(.*?)])?")
private val APPOP_LINE = Regex("^(?:Uid mode: )?([A-Z][A-Z0-9_]*): (allow|ignore|deny|foreground|default)\\b")

private fun indentOf(line: String): Int = line.length - line.trimStart().length

/** Reads the `runtime permissions:` list of user 0 from `dumpsys package <pkg>`. */
internal fun parsePackageDump(dump: String): PackageDump {
    val lines = dump.lines()
    val sharedUid = lines.any { val t = it.trim(); t.startsWith("sharedUser=") && t != "sharedUser=null" }
    val userIndex = lines.indexOfFirst { it.trim().startsWith("User 0:") }
    val permissions = mutableListOf<PermissionEntry>()
    if (userIndex >= 0) {
        var i = userIndex + 1
        while (i < lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.startsWith("User ")) break
            if (trimmed == "runtime permissions:") {
                val header = indentOf(lines[i])
                i++
                while (i < lines.size && lines[i].isNotBlank() && indentOf(lines[i]) > header) {
                    RUNTIME_LINE.find(lines[i].trim())?.let { m ->
                        val flags = m.groupValues[3].split('|').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                        permissions += PermissionEntry(m.groupValues[1], m.groupValues[2] == "true", flags)
                    }
                    i++
                }
                break
            }
            i++
        }
    }
    return PackageDump(permissions, sharedUid)
}

/** Reads `cmd appops get` output. A `Uid mode:` line wins over a package line for the same op. */
internal fun parseAppOps(output: String): List<AppOpEntry> {
    val byName = linkedMapOf<String, AppOpEntry>()
    for (line in output.lines()) {
        val trimmed = line.trim()
        val m = APPOP_LINE.find(trimmed) ?: continue
        val mode = AppOpMode.entries.first { it.argument == m.groupValues[2] }
        val uidMode = trimmed.startsWith("Uid mode:")
        if (uidMode || m.groupValues[1] !in byName) byName[m.groupValues[1]] = AppOpEntry(m.groupValues[1], mode)
    }
    return byName.values.toList()
}
