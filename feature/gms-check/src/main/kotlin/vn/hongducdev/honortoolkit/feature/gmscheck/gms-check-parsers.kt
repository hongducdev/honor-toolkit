package vn.hongducdev.honortoolkit.feature.gmscheck

private val PACKAGE_LINE = Regex("^package:(.+)$")
private val VERSION_NAME = Regex("^\\s*versionName=(\\S+)", RegexOption.MULTILINE)
private val GOOGLE_ACCOUNT = Regex("Account \\{name=[^,]*, type=com\\.google\\}")

/** `pm list packages [-d]` prints `package:<name>` per line. */
internal fun parsePackageNames(output: String): Set<String> =
    output.lineSequence().mapNotNull { PACKAGE_LINE.find(it.trim())?.groupValues?.get(1) }.toSet()

/** The first `Package [name]` block is the active one; an older system copy can follow it. */
internal fun parseVersionName(dump: String, packageName: String): String? {
    val start = dump.indexOf("Package [$packageName]")
    if (start < 0) return null
    return VERSION_NAME.find(dump, start)?.groupValues?.get(1)
}

/** Counts Google accounts only; names and addresses are never kept. */
internal fun countGoogleAccounts(dump: String): Int = GOOGLE_ACCOUNT.findAll(dump).count()
