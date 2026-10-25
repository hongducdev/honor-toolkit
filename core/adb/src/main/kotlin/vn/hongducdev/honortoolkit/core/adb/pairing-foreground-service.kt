package vn.hongducdev.honortoolkit.core.adb

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while the user opens "Pair with code" in Developer options, then takes the
 * six-digit code from a notification reply so the dialog can stay on screen.
 */
class PairingForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var session: AdbSession
    private lateinit var discovery: PairingDiscovery
    private lateinit var notifications: NotificationManager
    private var timeout: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        session = AdbSession.get(this)
        discovery = PairingDiscovery(this, session.diagnostics)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.pairing_channel_name), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SUBMIT_CODE) {
            submitCode(RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)?.toString().orEmpty())
        } else {
            beginSearching()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        discovery.stop()
        scope.cancel()
        super.onDestroy()
    }

    private fun beginSearching() {
        startForeground(
            NOTIFICATION_ID,
            notification(getString(R.string.pairing_searching), ongoing = true, withCodeInput = false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        discovery.start { port ->
            session.publishPairingPort(port)
            notifications.notify(
                NOTIFICATION_ID,
                notification(getString(R.string.pairing_enter_code), ongoing = true, withCodeInput = true),
            )
        }
        timeout = scope.launch {
            delay(PAIRING_WINDOW_MS)
            finish(getString(R.string.pairing_timed_out))
        }
    }

    private fun submitCode(code: String) {
        val port = session.pairingPort.value
        if (port == null || code.length != CODE_LENGTH) {
            finish(getString(R.string.pairing_failed))
            return
        }
        scope.launch {
            val result = session.pairAndConnect(port, code)
            finish(getString(if (result == AdbState.Connected) R.string.pairing_done else R.string.pairing_failed))
        }
    }

    private fun finish(message: String) {
        discovery.stop()
        timeout?.cancel()
        session.publishPairingPort(null)
        stopForeground(STOP_FOREGROUND_DETACH)
        notifications.notify(NOTIFICATION_ID, notification(message, ongoing = false, withCodeInput = false))
        stopSelf()
    }

    private fun notification(text: String, ongoing: Boolean, withCodeInput: Boolean): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_adb)
            .setContentTitle(getString(R.string.pairing_title))
            .setContentText(text)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
        if (withCodeInput) {
            val submit = PendingIntent.getForegroundService(
                this,
                0,
                Intent(this, PairingForegroundService::class.java).setAction(ACTION_SUBMIT_CODE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            val input = RemoteInput.Builder(KEY_CODE).setLabel(getString(R.string.pairing_code_label)).build()
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.pairing_enter_code_action), submit)
                    .addRemoteInput(input)
                    .build(),
            )
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_ID = "pairing"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_SUBMIT_CODE = "vn.hongducdev.honortoolkit.core.adb.SUBMIT_CODE"
        private const val KEY_CODE = "code"
        private const val CODE_LENGTH = 6
        private const val PAIRING_WINDOW_MS = 3 * 60_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, PairingForegroundService::class.java))
        }
    }
}
