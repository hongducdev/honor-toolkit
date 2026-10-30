package vn.hongducdev.honortoolkit.feature.circlesearch

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
//
// It runs as a foreground service: MagicOS kills plain background services, and the notification
// is the only reliable way to keep the strip alive and let the user turn it off.
class NavBarStripService : Service() {
    private val main = Handler(Looper.getMainLooper())
    private var strip: View? = null
    private var capturing = false
    private val restoreCapture = Runnable { restoreAfterCapture() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.cs_strip_notification_channel), NotificationManager.IMPORTANCE_MIN),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            TriggerConfig(this).stripEnabled = false
            stopStrip()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!attachStrip()) {
            stopStrip()
            return START_NOT_STICKY
        }
        scheduleHealthCheck()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!attachStrip()) stopStrip()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        capturing = false
        detachStrip()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (instance === this) instance = null
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
        val view = StripView(this, config)
        // The system can revoke the overlay window at any time; a failed re-add must stop the
        // service instead of crashing it, so the notification does not claim a strip that is gone.
        val added = runCatching { getSystemService(WindowManager::class.java).addView(view, params) }.isSuccess
        if (!added) return false
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

    private fun startForegroundCompat() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        val open = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, NavBarStripService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle(getString(R.string.cs_strip_notification_title))
            .setContentText(getString(R.string.cs_strip_notification_text))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.cs_strip_notification_stop), stop).build())
            .build()
    }

    private fun stopStrip() {
        main.removeCallbacksAndMessages(null)
        detachStrip()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleHealthCheck() {
        main.removeCallbacks(healthCheck)
        main.postDelayed(healthCheck, HEALTH_MS)
    }

    private val healthCheck = Runnable {
        val view = strip
        if (view == null || !view.isAttachedToWindow) {
            if (!attachStrip()) {
                stopStrip()
                return@Runnable
            }
        }
        scheduleHealthCheck()
    }

    private fun restoreAfterCapture() {
        capturing = false
        strip?.visibility = View.VISIBLE
    }

    private class StripView(context: Context, private val config: TriggerConfig) : View(context) {
        private val detector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            // Single tap is confirmed only after the double-tap timeout, so a double tap does not
            // also fire the tap action.
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                dispatch(StripGesture.Tap)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                dispatch(StripGesture.Double)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                dispatch(StripGesture.Hold)
            }
        })

        init {
            setBackgroundColor(if (config.stripVisible) VISIBLE_DEBUG_COLOR else Color.TRANSPARENT)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean = detector.onTouchEvent(event)

        private fun dispatch(gesture: StripGesture) {
            when (config.stripAction(gesture)) {
                StripAction.None -> return
                StripAction.Circle -> TriggerRunner.run(context, 0, TriggerSource.Strip)
                StripAction.Gemini -> TriggerRunner.runGemini(context, 0, TriggerSource.Strip)
                StripAction.Assistant -> TriggerRunner.runAssistant(context, 0, TriggerSource.Strip)
            }
            performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    companion object {
        private const val VISIBLE_DEBUG_COLOR = 0x55FF0000
        private const val CHANNEL_ID = "nav_strip"
        private const val NOTIFICATION_ID = 2
        private const val ACTION_STOP = "vn.hongducdev.honortoolkit.feature.circlesearch.STOP_STRIP"
        private const val HEALTH_MS = 30_000L
        private const val CAPTURE_SETTLE_MS = 90L
        private const val CAPTURE_HOLD_MS = 1000L

        private val captureHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var instance: NavBarStripService? = null

        /**
         * Hides the strip just before a launch so it does not end up in the screenshot Circle to Search
         * takes, then shows it again. The action still runs when the service is stopping, because it
         * is posted on a handler the service does not own.
         */
        fun hideForCapture(action: () -> Unit) {
            val service = instance
            val view = service?.strip
            if (service == null || view == null || service.capturing) {
                action()
                return
            }
            service.capturing = true
            view.visibility = View.INVISIBLE
            service.main.removeCallbacks(service.restoreCapture)
            captureHandler.postDelayed(action, CAPTURE_SETTLE_MS)
            service.main.postDelayed(service.restoreCapture, CAPTURE_HOLD_MS)
        }

        // Starts or stops the strip so it matches the saved settings and the overlay permission.
        // Starting a foreground service can be refused from the background; a failed start must not
        // crash the caller (boot receiver, tile or screen), it just leaves the strip off.
        fun sync(context: Context) {
            val intent = Intent(context, NavBarStripService::class.java)
            if (TriggerConfig(context).stripEnabled && Settings.canDrawOverlays(context)) {
                runCatching { context.startForegroundService(intent) }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
