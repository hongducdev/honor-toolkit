package vn.hongducdev.honortoolkit.feature.notificationfix

private val NOTIFICATION_LINE = Regex("android\\.permission\\.POST_NOTIFICATIONS: granted=(true|false)")
private val APPOP_LINE = Regex("^(Uid mode: )?[A-Z][A-Z0-9_]*: (allow|ignore|deny|foreground|default)\\b")
private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")

/** Package names go straight into a shell command line, so anything unusual is rejected. */
internal fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

/** Null when the app has no runtime notification permission (declared nowhere, or Android below 13). */
internal fun parseNotificationGranted(dump: String): Boolean? =
    NOTIFICATION_LINE.find(dump)?.groupValues?.get(1)?.let { it == "true" }

internal fun declaresExactAlarm(dump: String): Boolean = dump.contains("android.permission.SCHEDULE_EXACT_ALARM")

/** `cmd deviceidle whitelist` prints `<source>,<package>,<uid>` per line. */
internal fun isDozeWhitelisted(output: String, packageName: String): Boolean =
    output.lineSequence().any { line -> line.split(',').getOrNull(1)?.trim() == packageName }

/** True only for an explicit `allow`. A non-default `Uid mode:` line wins over the package line. Null when the op was not listed. */
internal fun parseAppOpAllowed(output: String): Boolean? {
    var packageMode: String? = null
    for (line in output.lines()) {
        val m = APPOP_LINE.find(line.trim()) ?: continue
        val mode = m.groupValues[2]
        if (m.groupValues[1].isNotEmpty()) {
            if (mode != "default") return mode == "allow"
        } else if (packageMode == null) {
            packageMode = mode
        }
    }
    return packageMode?.let { it == "allow" }
}

/** `am get-standby-bucket` prints the numeric bucket (10 active, 20 working set, 30 frequent, 40 rare, 45 restricted). */
internal fun parseStandbyBucket(output: String): Int? = output.trim().toIntOrNull()
