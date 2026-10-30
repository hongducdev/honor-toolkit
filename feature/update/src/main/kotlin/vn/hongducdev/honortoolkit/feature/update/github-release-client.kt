package vn.hongducdev.honortoolkit.feature.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Reads releases from the GitHub REST API with plain `HttpURLConnection`; the module carries no HTTP
 * library. Unauthenticated requests are limited to 60 per hour per address, enough for the launch
 * check plus manual taps.
 */
class GitHubReleaseClient(private val repo: String) {

    fun latest(): GitHubRelease {
        val connection = open("$ApiBase/repos/$repo/releases/latest", acceptJson = true)
        try {
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Unit
                HttpURLConnection.HTTP_NOT_FOUND -> throw UpdateException("Kho GitHub chưa có bản phát hành nào.")
                HttpURLConnection.HTTP_FORBIDDEN -> throw UpdateException("GitHub tạm chặn yêu cầu (giới hạn lượt gọi). Thử lại sau.")
                else -> throw UpdateException("GitHub trả về lỗi $code.")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseLatestRelease(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the release APK to [target], reporting progress 0..1 and verifying the SHA-256 digest. */
    fun download(asset: ReleaseAsset, target: File, onProgress: (Float) -> Unit) {
        val temp = File(target.parentFile, target.name + ".part")
        temp.parentFile?.mkdirs()
        val connection = open(asset.url, acceptJson = false)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw UpdateException("Tải APK thất bại (HTTP ${connection.responseCode}).")
            }
            val total = if (asset.size > 0) asset.size else connection.contentLengthLong
            var copied = 0L
            connection.inputStream.use { input ->
                temp.outputStream().use { output ->
                    val buffer = ByteArray(BufferSize)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            if (asset.size > 0 && temp.length() != asset.size) {
                throw UpdateException("Tệp tải về không đủ dung lượng (${temp.length()}/$total byte).")
            }
            asset.digest?.let { verifyDigest(temp, it) }
            if (target.exists() && !target.delete()) throw UpdateException("Không xoá được bản cập nhật cũ.")
            if (!temp.renameTo(target)) throw UpdateException("Không lưu được tệp APK.")
            onProgress(1f)
        } finally {
            connection.disconnect()
            if (temp.exists()) temp.delete()
        }
    }

    private fun open(url: String, acceptJson: Boolean): HttpURLConnection =
        (URL(url).openConnection() as? HttpURLConnection)
            ?.apply {
                instanceFollowRedirects = true
                connectTimeout = ConnectTimeoutMs
                readTimeout = ReadTimeoutMs
                setRequestProperty("User-Agent", "HonorToolkit-Updater")
                if (acceptJson) {
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                }
            }
            ?: throw IOException("Địa chỉ tải không hợp lệ: $url")

    private fun verifyDigest(file: File, digest: String) {
        val algorithm = digest.substringBefore(':', "").lowercase()
        val expected = digest.substringAfter(':', "").lowercase()
        if (algorithm != "sha256" || expected.isEmpty()) return
        val actual = MessageDigest.getInstance("SHA-256").run {
            file.inputStream().use { input ->
                val buffer = ByteArray(BufferSize)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    update(buffer, 0, read)
                }
            }
            digest().joinToString("") { "%02x".format(it) }
        }
        if (actual != expected) throw UpdateException("Tệp tải về không khớp mã kiểm tra SHA-256.")
    }

    private companion object {
        const val ApiBase = "https://api.github.com"
        const val BufferSize = 64 * 1024
        const val ConnectTimeoutMs = 15_000
        const val ReadTimeoutMs = 30_000
    }
}
