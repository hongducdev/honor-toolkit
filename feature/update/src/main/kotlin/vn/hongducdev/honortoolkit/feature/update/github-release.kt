package vn.hongducdev.honortoolkit.feature.update

import org.json.JSONObject

/** A downloadable file attached to a GitHub release. [digest] is `sha256:<hex>` when GitHub provides one. */
data class ReleaseAsset(val name: String, val url: String, val size: Long, val digest: String?)

/** The parts of a GitHub release the updater needs. */
data class GitHubRelease(
    val tag: String,
    val version: AppVersion,
    val title: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val apk: ReleaseAsset?,
)

/** A release that is newer than the installed app, ready to download and install. */
data class AvailableUpdate(
    val versionName: String,
    val notes: String,
    val pageUrl: String,
    val publishedAt: String,
    val apkName: String,
    val apkUrl: String,
    val apkSize: Long,
    val digest: String?,
)

/** A failure the update screen can show as-is. Message is user-facing Vietnamese. */
class UpdateException(message: String) : Exception(message)

/**
 * Parses `GET /repos/{owner}/{repo}/releases/latest`. The first APK asset wins, preferring one
 * whose name mentions the app so a debug or symbol archive attached to the release is not picked.
 */
internal fun parseLatestRelease(payload: String): GitHubRelease {
    val root = JSONObject(payload)
    val tag = root.optString("tag_name")
    val version = AppVersion.parse(tag)
        ?: throw UpdateException("Thẻ phát hành \"$tag\" không phải phiên bản dạng x.y.z.")

    val assets = root.optJSONArray("assets")
    val apks = buildList {
        if (assets == null) return@buildList
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            add(
                ReleaseAsset(
                    name = name,
                    url = asset.optString("browser_download_url"),
                    size = asset.optLong("size"),
                    digest = asset.optString("digest").takeIf { it.isNotBlank() },
                ),
            )
        }
    }

    val namedApks = apks.filter { it.name.contains("honor-toolkit", ignoreCase = true) }
    val preferred = namedApks.firstOrNull {
        !it.name.contains("debug", ignoreCase = true) && !it.name.contains("unsigned", ignoreCase = true)
    }
    return GitHubRelease(
        tag = tag,
        version = version,
        title = root.optString("name").ifBlank { tag },
        notes = root.optString("body").trim(),
        pageUrl = root.optString("html_url"),
        publishedAt = root.optString("published_at"),
        apk = preferred ?: namedApks.firstOrNull() ?: apks.firstOrNull(),
    )
}
