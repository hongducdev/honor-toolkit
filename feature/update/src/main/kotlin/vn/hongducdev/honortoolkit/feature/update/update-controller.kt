package vn.hongducdev.honortoolkit.feature.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/** Result of one GitHub check. */
sealed interface CheckResult {
    data class Newer(val update: AvailableUpdate) : CheckResult
    data object UpToDate : CheckResult
}

/** Result of asking Android to install the downloaded APK. */
sealed interface InstallResult {
    data object Started : InstallResult
    data object NeedsPermission : InstallResult
}

/**
 * Checks GitHub releases, downloads the APK next to the app cache and hands it to the system
 * installer. Blocking calls run on the caller's thread; the view model moves them to IO.
 */
class UpdateController(context: Context) {
    private val context = context.applicationContext
    private val config = UpdateConfig(this.context)
    private val client = GitHubReleaseClient(BuildConfig.GITHUB_REPO)
    private val packageManager: PackageManager = this.context.packageManager

    val currentVersionName: String = packageInfo()?.versionName ?: UnknownVersion
    val currentVersionCode: Long = packageInfo()?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L

    var autoCheck: Boolean
        get() = config.autoCheck
        set(value) { config.autoCheck = value }

    var lastCheckedAt: Long
        get() = config.lastCheckedAt
        set(value) { config.lastCheckedAt = value }

    var skippedVersion: String?
        get() = config.skippedVersion
        set(value) { config.skippedVersion = value }

    fun checkNow(): CheckResult {
        // Recorded before the request so an offline phone retries on the next launch, not in a loop.
        config.lastCheckedAt = System.currentTimeMillis()
        val release = client.latest()
        val apk = release.apk ?: throw UpdateException("Bản phát hành ${release.tag} không kèm tệp APK.")
        val installed = AppVersion.parse(currentVersionName)
        if (installed != null && release.version <= installed) return CheckResult.UpToDate
        return CheckResult.Newer(
            AvailableUpdate(
                versionName = release.version.toString(),
                notes = release.notes,
                pageUrl = release.pageUrl,
                publishedAt = release.publishedAt,
                apkName = sanitizeFileName(apk.name),
                apkUrl = apk.url,
                apkSize = apk.size,
                digest = apk.digest,
            ),
        )
    }

    fun download(update: AvailableUpdate, onProgress: (Float) -> Unit): File {
        val target = File(updateDir(), update.apkName)
        client.download(ReleaseAsset(update.apkName, update.apkUrl, update.apkSize, update.digest), target, onProgress)
        verifyApk(target)
        return target
    }

    fun canInstall(): Boolean = packageManager.canRequestPackageInstalls()

    fun install(apk: File): InstallResult {
        if (!canInstall()) {
            openInstallPermissionSettings()
            return InstallResult.NeedsPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, ApkMimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return InstallResult.Started
    }

    fun openInstallPermissionSettings() {
        val packageUri = Uri.parse("package:${context.packageName}")
        val opened = runCatching {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.isSuccess
        if (!opened) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun openReleasePage(url: String) {
        val opened = runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (!opened) throw UpdateException("Không mở được trình duyệt.")
    }

    /** Removes leftovers of a cancelled or replaced download. */
    fun clearDownloads() {
        updateDir().listFiles()?.forEach { it.delete() }
    }

    private fun verifyApk(apk: File) {
        val info = packageManager.getPackageArchiveInfo(apk.absolutePath, 0)
            ?: throw UpdateException("Tệp tải về không phải APK hợp lệ.")
        if (info.packageName != context.packageName) {
            throw UpdateException("APK tải về thuộc ứng dụng khác (${info.packageName}).")
        }
    }

    private fun updateDir(): File = File(context.cacheDir, "updates")

    private fun packageInfo() = runCatching { packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "honor-toolkit.apk" }.let {
            if (it.endsWith(".apk", ignoreCase = true)) it else "$it.apk"
        }

    private companion object {
        const val ApkMimeType = "application/vnd.android.package-archive"
        const val UnknownVersion = "0"
    }
}
