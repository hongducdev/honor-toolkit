package vn.hongducdev.honortoolkit.feature.tune

/** Prefix the batch read writes before every value so keys can be matched back to their setting. */
internal const val SETTINGS_MARKER = "__HT_"

/**
 * Parses the output of the batched `echo -n 'marker='; settings get ns key` command.
 * A missing key, an empty value and the literal `null` all mean "this ROM does not expose it".
 */
internal fun parseSettingsDump(output: String): Map<SettingKey, String?> {
    val values = mutableMapOf<SettingKey, String?>()
    for (rawLine in output.lineSequence()) {
        val line = rawLine.trim()
        if (!line.startsWith(SETTINGS_MARKER)) continue
        val body = line.removePrefix(SETTINGS_MARKER)
        val separator = body.indexOf('=')
        if (separator <= 0) continue
        val path = body.substring(0, separator)
        val namespace = TuneNamespace.entries.firstOrNull { path.startsWith("${it.cli}/") } ?: continue
        val key = path.substring(namespace.cli.length + 1)
        if (key.isEmpty()) continue
        val value = body.substring(separator + 1).trim()
        values[SettingKey(namespace, key)] = value.takeIf { it.isNotEmpty() && it != "null" }
    }
    return values
}
