package vn.hongducdev.honortoolkit.feature.quickshare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.DiagnosticLog
import vn.hongducdev.honortoolkit.core.quickshare.connection.ConsentResponder
import vn.hongducdev.honortoolkit.core.quickshare.connection.FileSink
import vn.hongducdev.honortoolkit.core.quickshare.connection.InboundConnection
import vn.hongducdev.honortoolkit.core.quickshare.connection.InboundListener
import vn.hongducdev.honortoolkit.core.quickshare.connection.OfferedFile
import vn.hongducdev.honortoolkit.core.quickshare.connection.TransferOffer
import vn.hongducdev.honortoolkit.core.quickshare.connection.TransferOutcome
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.DeviceType
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointInfo
import vn.hongducdev.honortoolkit.core.quickshare.endpoint.EndpointNaming
import java.io.IOException
import java.net.ServerSocket
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Listens for Quick Share senders on the local network while the user keeps receiving switched on. */
class QuickShareReceiverService : Service() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connections = ConcurrentHashMap.newKeySet<InboundConnection>()
    private val pendingOffers = ConcurrentHashMap<Int, ConsentResponder>()
    private val nextOfferId = AtomicInteger(OFFER_ID_BASE)
    private lateinit var log: DiagnosticLog
    private lateinit var notifications: NotificationManager
    private lateinit var advertiser: MdnsAdvertiser
    private var executor: ExecutorService? = null
    private var serverSocket: ServerSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        log = AdbSession.get(this).diagnostics
        advertiser = MdnsAdvertiser(this, log)
        notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, getString(R.string.quick_share_channel_service), NotificationManager.IMPORTANCE_LOW),
        )
        notifications.createNotificationChannel(
            NotificationChannel(OFFER_CHANNEL_ID, getString(R.string.quick_share_channel_offer), NotificationManager.IMPORTANCE_HIGH),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> shutdown()
            ACTION_ACCEPT -> respond(intent.getIntExtra(EXTRA_OFFER_ID, -1), accept = true)
            ACTION_DECLINE -> respond(intent.getIntExtra(EXTRA_OFFER_ID, -1), accept = false)
            else -> startReceiving()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopListening()
        super.onDestroy()
    }

    private fun startReceiving() {
        if (serverSocket != null) return
        startForeground(
            SERVICE_NOTIFICATION_ID,
            serviceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        val socket = try {
            ServerSocket(0)
        } catch (e: IOException) {
            log.record("quick share: cannot open a listening socket", e)
            shutdown()
            return
        }
        serverSocket = socket
        val pool = Executors.newCachedThreadPool()
        executor = pool
        val name = deviceName()
        val endpointId = EndpointNaming.newEndpointId()
        advertiser.start(endpointId, EndpointInfo(name, DeviceType.PHONE), socket.localPort)
        QuickShareReceiverState.update {
            it.copy(running = true, deviceName = name, progressPercent = null, serviceName = EndpointNaming.mdnsServiceName(endpointId))
        }
        pool.execute { acceptLoop(socket, pool) }
    }

    private fun acceptLoop(socket: ServerSocket, pool: ExecutorService) {
        while (!socket.isClosed) {
            val peer = try {
                socket.accept()
            } catch (e: SocketException) {
                if (!socket.isClosed) log.record("quick share: accept failed", e)
                return
            }
            val connection = InboundConnection(peer, OfferHandler())
            connections += connection
            pool.execute {
                try {
                    handleOutcome(connection.run())
                } finally {
                    connections -= connection
                }
            }
        }
    }

    private fun handleOutcome(outcome: TransferOutcome) {
        val last = when (outcome) {
            TransferOutcome.Completed -> LastResult.DONE
            is TransferOutcome.Rejected -> LastResult.REJECTED
            is TransferOutcome.Cancelled -> LastResult.CANCELLED
            is TransferOutcome.Failed -> {
                log.record("quick share: transfer failed", outcome.cause)
                LastResult.FAILED
            }
        }
        QuickShareReceiverState.update { it.copy(progressPercent = null, last = last) }
    }

    /** Answering writes to the peer's socket, which Android forbids on the main thread. */
    private fun respond(id: Int, accept: Boolean) {
        val responder = pendingOffers.remove(id) ?: return
        QuickShareReceiverState.update { if (it.offer?.id == id) it.copy(offer = null) else it }
        if (!accept) notifications.cancel(id)
        executor?.execute {
            try {
                if (accept) responder.accept() else responder.reject()
            } catch (e: RuntimeException) {
                log.record("quick share: answering the offer failed", e)
            }
        }
    }

    private fun shutdown() {
        stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopListening() {
        advertiser.stop()
        serverSocket?.close()
        serverSocket = null
        connections.forEach(InboundConnection::cancel)
        connections.clear()
        executor?.shutdownNow()
        executor = null
        pendingOffers.keys.forEach(notifications::cancel)
        pendingOffers.clear()
        QuickShareReceiverState.update { it.copy(running = false, progressPercent = null, offer = null, serviceName = null) }
    }

    private fun deviceName(): String =
        Settings.Global.getString(contentResolver, "device_name")?.takeIf { it.isNotBlank() } ?: Build.MODEL

    private inner class OfferHandler : InboundListener {
        private var offerId = 0
        private var lastPercent = -1

        override fun onOffer(offer: TransferOffer, responder: ConsentResponder) {
            offerId = nextOfferId.getAndIncrement()
            pendingOffers[offerId] = responder
            val pending = PendingOffer(offerId, offer.device.name, offer.pin, offer.files.size)
            QuickShareReceiverState.update { it.copy(offer = pending) }
            notifications.notify(offerId, offerNotification(offerId, offer))
            val id = offerId
            mainHandler.postDelayed({ respond(id, accept = false) }, OFFER_TIMEOUT_MS)
        }

        override fun openSink(file: OfferedFile): FileSink = MediaStoreFileSink(contentResolver, file, log)

        override fun onProgress(bytesReceived: Long, bytesTotal: Long) {
            val percent = if (bytesTotal > 0) (bytesReceived * 100 / bytesTotal).toInt() else 0
            if (percent == lastPercent) return
            lastPercent = percent
            QuickShareReceiverState.update { it.copy(progressPercent = percent) }
            notifications.notify(offerId, progressNotification(percent))
            if (percent == 100) notifications.notify(offerId, receivedNotification())
        }

        override fun onTextReceived(text: String) {
            notifications.notify(offerId, textNotification(text))
        }
    }

    private fun serviceNotification(): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, QuickShareReceiverService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_quickshare)
            .setContentTitle(getString(R.string.quick_share_title))
            .setContentText(getString(R.string.quick_share_service_text))
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.quick_share_stop), stop).build())
            .build()
    }

    private fun offerNotification(id: Int, offer: TransferOffer): Notification {
        val what = when {
            offer.files.isNotEmpty() -> getString(R.string.quick_share_offer_files, offer.files.size)
            else -> getString(R.string.quick_share_offer_text_item)
        }
        return Notification.Builder(this, OFFER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_quickshare)
            .setContentTitle(getString(R.string.quick_share_offer_title, offer.device.name))
            .setContentText(getString(R.string.quick_share_offer_text, offer.pin, what))
            .addAction(Notification.Action.Builder(null, getString(R.string.quick_share_accept), offerIntent(id, ACTION_ACCEPT)).build())
            .addAction(Notification.Action.Builder(null, getString(R.string.quick_share_decline), offerIntent(id, ACTION_DECLINE)).build())
            .setOngoing(true)
            .promoted()
            .build()
    }

    /**
     * Shows an ongoing notification in Honor's Magic Capsule, which displays the elapsed-time chip.
     * The typed setter for the request only exists from SDK 37.
     */
    private fun Notification.Builder.promoted(): Notification.Builder =
        setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setUsesChronometer(true)
            .addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })

    private fun offerIntent(id: Int, action: String): PendingIntent = PendingIntent.getService(
        this,
        id * 2 + if (action == ACTION_ACCEPT) 0 else 1,
        Intent(this, QuickShareReceiverService::class.java).setAction(action).putExtra(EXTRA_OFFER_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(percent: Int): Notification =
        Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_quickshare)
            .setContentTitle(getString(R.string.quick_share_title))
            .setContentText(getString(R.string.quick_share_status_receiving, percent))
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .promoted()
            .build()

    private fun receivedNotification(): Notification =
        Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification_quickshare)
            .setContentTitle(getString(R.string.quick_share_received_title))
            .setContentText(getString(R.string.quick_share_received_text))
            .setAutoCancel(true)
            .build()

    private fun textNotification(text: String): Notification {
        val builder = Notification.Builder(this, OFFER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_quickshare)
            .setContentTitle(getString(R.string.quick_share_received_title))
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setAutoCancel(true)
        val uri = Uri.parse(text.trim())
        if (uri.scheme == "http" || uri.scheme == "https") {
            builder.setContentIntent(
                PendingIntent.getActivity(this, 0, Intent(Intent.ACTION_VIEW, uri), PendingIntent.FLAG_IMMUTABLE),
            )
        }
        return builder.build()
    }

    companion object {
        private const val CHANNEL_SERVICE = "quick_share_service"
        const val OFFER_CHANNEL_ID = "quick_share_offer"
        private const val SERVICE_NOTIFICATION_ID = 10
        private const val OFFER_ID_BASE = 100
        private const val OFFER_TIMEOUT_MS = 60_000L
        private const val ACTION_STOP = "vn.hongducdev.honortoolkit.feature.quickshare.STOP"
        private const val ACTION_ACCEPT = "vn.hongducdev.honortoolkit.feature.quickshare.ACCEPT"
        private const val ACTION_DECLINE = "vn.hongducdev.honortoolkit.feature.quickshare.DECLINE"
        private const val EXTRA_OFFER_ID = "offer_id"
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

        fun start(context: Context) {
            context.startForegroundService(Intent(context, QuickShareReceiverService::class.java))
        }

        internal fun answer(context: Context, offerId: Int, accept: Boolean) {
            context.startService(
                Intent(context, QuickShareReceiverService::class.java)
                    .setAction(if (accept) ACTION_ACCEPT else ACTION_DECLINE)
                    .putExtra(EXTRA_OFFER_ID, offerId),
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, QuickShareReceiverService::class.java).setAction(ACTION_STOP))
        }
    }
}
