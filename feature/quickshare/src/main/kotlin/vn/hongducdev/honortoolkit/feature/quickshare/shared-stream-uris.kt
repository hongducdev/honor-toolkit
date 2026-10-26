package vn.hongducdev.honortoolkit.feature.quickshare

import android.content.Intent
import android.net.Uri
import android.os.Build

/** The files another app shares through ACTION_SEND or ACTION_SEND_MULTIPLE. */
fun sharedStreamUris(intent: Intent): List<Uri> = when (intent.action) {
    Intent.ACTION_SEND -> listOfNotNull(intent.streamUri())
    Intent.ACTION_SEND_MULTIPLE -> intent.streamUris()
    else -> emptyList()
}

private fun Intent.streamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }
