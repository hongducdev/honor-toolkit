package vn.hongducdev.honortoolkit.feature.circlesearch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.provider.Settings
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

// A transparent strip laid over the system navigation handle. It only listens for a long press;
// swipes starting on it are taken over by the system gesture handler.
class NavBarStripService : Service() {
    private var strip: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        if (!attachStrip()) stopSelf()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!attachStrip()) stopSelf()
    }

    override fun onDestroy() {
        detachStrip()
        super.onDestroy()
    }

    private fun attachStrip(): Boolean {
        detachStrip()
        if (!Settings.canDrawOverlays(this)) return false
        val config = TriggerConfig(this)
        val screenWidth = resources.displayMetrics.widthPixels
        val params = WindowManager.LayoutParams(
            (screenWidth * config.stripWidthPercent / 100).toInt(),
            dp(config.stripHeightDp.toInt()),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            fitInsetsTypes = 0
            y = (navigationBarHeightPx() + dp(config.stripOffsetDp.toInt())).coerceAtLeast(0)
        }
        val view = StripView(this, config.stripVisible)
        getSystemService(WindowManager::class.java).addView(view, params)
        strip = view
        return true
    }

    // The system navigation bar window sits above app overlays and swallows touches, so the strip must start above it.
    private fun navigationBarHeightPx(): Int =
        getSystemService(WindowManager::class.java).currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun detachStrip() {
        strip?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        strip = null
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.strip_notification_title), NotificationManager.IMPORTANCE_MIN),
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.strip_notification_title))
            .setContentText(getString(R.string.strip_notification_text))
            .setOngoing(true)
            .build()
    }

    private class StripView(context: Context, visible: Boolean) : View(context) {
        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onLongPress(e: MotionEvent) {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                TriggerRunner.run(context, 0)
            }
        })

        init {
            setBackgroundColor(if (visible) VISIBLE_DEBUG_COLOR else Color.TRANSPARENT)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "strip"
        private const val VISIBLE_DEBUG_COLOR = 0x55FF0000

        // Starts or stops the strip so it matches the saved settings and the overlay permission.
        fun sync(context: Context) {
            val intent = Intent(context, NavBarStripService::class.java)
            if (TriggerConfig(context).stripEnabled && Settings.canDrawOverlays(context)) {
                context.startForegroundService(intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
