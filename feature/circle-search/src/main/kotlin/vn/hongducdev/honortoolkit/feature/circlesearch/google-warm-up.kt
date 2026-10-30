package vn.hongducdev.honortoolkit.feature.circlesearch

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/**
 * Wakes the Google app before asking it for an assist session. A frozen process refuses the
 * request, which is the usual reason the first launch after a while fails, so the caller retries
 * once after this. Mirrors the Gesture Search warm-up: touch a few Google content providers, bind
 * the SSB service and send its wake-up broadcast.
 */
internal object GoogleWarmUp {
    private const val TAG = "HonorToolkit"
    private const val GOOGLE = "com.google.android.googlequicksearchbox"
    private const val SSB_SERVICE = "com.google.android.ssb.action.SSB_SERVICE"
    private const val WAKEUP_ACTION = "com.google.android.googlequicksearchbox.ACTION_WAKEUP"
    private const val MAX_PROVIDERS = 4
    private const val PREWARM_INTERVAL_MS = 5_000L
    private const val UNBIND_DELAY_MS = 250L

    // Intent.FLAG_RECEIVER_INCLUDE_BACKGROUND is hidden; the raw value delivers the wake-up to a
    // background receiver, which is the point of the broadcast.
    private const val RECEIVER_INCLUDE_BACKGROUND = 0x01000000

    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var lastPrewarm = 0L

    /** Warms Google in the background, at most once every 5 s, so the next request is cheap. */
    fun prewarm(context: Context) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastPrewarm < PREWARM_INTERVAL_MS) return
        lastPrewarm = now
        val app = context.applicationContext
        Thread({ warmUpNow(app) }, "google-prewarm").start()
    }

    /** Runs on the caller's thread; every step is best-effort and failures are ignored. */
    fun warmUp(context: Context) {
        runCatching { warmUpNow(context.applicationContext) }
            .onFailure { Log.w(TAG, "google warm-up failed", it) }
    }

    @SuppressLint("WrongConstant")
    private fun warmUpNow(context: Context) {
        touch(context.contentResolver, "$GOOGLE.GsaPublicContentProvider")
        touch(context.contentResolver, "$GOOGLE.AudioProvider")
        exportedProviders(context).forEach { touch(context.contentResolver, it) }
        bindSsb(context)
        context.sendBroadcast(
            Intent(WAKEUP_ACTION)
                .setPackage(GOOGLE)
                .addFlags(RECEIVER_INCLUDE_BACKGROUND or Intent.FLAG_RECEIVER_FOREGROUND),
        )
    }

    private fun exportedProviders(context: Context): List<String> =
        runCatching {
            context.packageManager.getPackageInfo(GOOGLE, PackageManager.GET_PROVIDERS).providers
                ?.asSequence()
                ?.filter { it.exported && !it.authority.isNullOrEmpty() }
                ?.mapNotNull { it.authority }
                ?.take(MAX_PROVIDERS)
                ?.toList()
                .orEmpty()
        }.getOrDefault(emptyList())

    private fun touch(resolver: ContentResolver, authority: String) {
        runCatching { resolver.acquireUnstableContentProviderClient(authority)?.close() }
    }

    private fun bindSsb(context: Context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) = unbind(context, this)

            override fun onServiceDisconnected(name: ComponentName?) = Unit
        }
        val bound = runCatching {
            context.bindService(
                Intent(SSB_SERVICE).setPackage(GOOGLE),
                connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT,
            )
        }.getOrDefault(false)
        if (bound) main.postDelayed({ unbind(context, connection) }, UNBIND_DELAY_MS)
    }

    private fun unbind(context: Context, connection: ServiceConnection) {
        runCatching { context.unbindService(connection) }
    }
}
