package vn.hongducdev.honortoolkit.feature.update

/**
 * Version parsed from a release tag such as `v0.2.1`. Parts compare numerically, and a prerelease
 * (`1.0.0-beta.1`) ranks below the matching final release.
 */
data class AppVersion(val parts: List<Int>, val prerelease: String?) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int {
        for (index in 0 until maxOf(parts.size, other.parts.size)) {
            val left = parts.getOrElse(index) { 0 }
            val right = other.parts.getOrElse(index) { 0 }
            if (left != right) return left.compareTo(right)
        }
        return when {
            prerelease == null && other.prerelease == null -> 0
            prerelease == null -> 1
            other.prerelease == null -> -1
            else -> prerelease.compareTo(other.prerelease)
        }
    }

    override fun toString(): String = parts.joinToString(".") + (prerelease?.let { "-$it" } ?: "")

    companion object {
        fun parse(raw: String?): AppVersion? {
            val text = raw?.trim()?.removePrefix("v")?.removePrefix("V")?.takeIf { it.isNotEmpty() } ?: return null
            val main = text.substringBefore('-')
            val prerelease = text.substringAfter('-', "").ifEmpty { null }
            val parts = main.split('.').map { it.toIntOrNull() ?: return null }
            if (parts.isEmpty()) return null
            return AppVersion(parts, prerelease)
        }
    }
}
