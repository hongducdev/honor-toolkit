package vn.hongducdev.honortoolkit.feature.quickshare

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import vn.hongducdev.honortoolkit.core.adb.DiagnosticLog
import vn.hongducdev.honortoolkit.core.quickshare.connection.FileSink
import vn.hongducdev.honortoolkit.core.quickshare.connection.OfferedFile
import java.io.IOException
import java.io.OutputStream

/** Writes into Download/QuickShare through MediaStore; the entry stays pending until [commit] so half files never show up. */
internal class MediaStoreFileSink(
    private val resolver: ContentResolver,
    file: OfferedFile,
    private val log: DiagnosticLog,
) : FileSink {
    private val uri: Uri
    private val out: OutputStream

    init {
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, safeFileName(file.name))
            if (file.mimeType.isNotBlank()) put(MediaStore.Downloads.MIME_TYPE, file.mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore refused ${file.name}")
        out = resolver.openOutputStream(uri) ?: run {
            resolver.delete(uri, null, null)
            throw IOException("Cannot open ${file.name} for writing")
        }
    }

    override fun write(buffer: ByteArray, length: Int) = out.write(buffer, 0, length)

    override fun commit() {
        out.close()
        resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    override fun abort() {
        try {
            out.close()
        } catch (e: IOException) {
            log.record("quick share: closing aborted file failed", e)
        }
        resolver.delete(uri, null, null)
    }

    companion object {
        const val FOLDER = "QuickShare"

        /** The sender controls the name, so keep only its last segment and drop separators. */
        fun safeFileName(name: String): String {
            val cleaned = name.substringAfterLast('/').substringAfterLast('\\').replace(':', '_').trim()
            return cleaned.ifBlank { "file" }
        }
    }
}
