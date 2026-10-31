package vn.hongducdev.honortoolkit.feature.automation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.app.UiModeManager
import android.database.ContentObserver
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.CalendarContract
import android.os.Build
import android.os.IBinder
import android.provider.Settings

/**
 * Keeps the battery, screen, Wi-Fi, Bluetooth and clock receivers registered. Android does not allow
 * most of these broadcasts to be declared statically, and MagicOS kills plain background services,
 * so this is a foreground service with the quiet notification the other modules use.
 *
 * The service only runs while at least one rule is enabled. Rules themselves live in
 * device-protected storage, so a rule can still run after a reboot before the first unlock.
 */
class AutomationForegroundService : Service() {
    private lateinit var store: AutomationStore
    private var registered = false
    private var lastPercent: Int? = null
    private var lastPlugged: Boolean? = null
    private var lastWifi: Boolean? = null
    private var lastDnd: Boolean? = null
    private var lastPowerSave: Boolean? = null
    private var lastCarMode: Boolean? = null
    private var wifiNetwork: Network? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBatteryChanged(intent)
                Intent.ACTION_POWER_CONNECTED -> onPlugChanged(true)
                Intent.ACTION_POWER_DISCONNECTED -> onPlugChanged(false)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> {
                    dispatch(TriggerEvent.ScreenChanged(true))
                    evaluateStates()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    dispatch(TriggerEvent.ScreenChanged(false))
                    evaluateStates()
                }
                Intent.ACTION_USER_PRESENT -> dispatch(TriggerEvent.DeviceUnlocked)
            }
        }
    }

    private val modeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> updatePowerSave()
                UiModeManager.ACTION_ENTER_CAR_MODE -> updateCarMode(true)
                UiModeManager.ACTION_EXIT_CAR_MODE -> updateCarMode(false)
            }
        }
    }

    private val calendarObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            AutomationAlarms.sync(this@AutomationForegroundService)
            evaluateStates()
        }
    }

    private val dndReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED) updateDnd()
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            if (!AutomationPermissions.granted(context, AutomationRequirement.BluetoothConnect)) return
            val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            } ?: return
            val address = runCatching { device.address }.getOrNull() ?: return
            val name = runCatching { device.name }.getOrNull().orEmpty()
            dispatch(
                TriggerEvent.BluetoothChanged(
                    address = address,
                    name = name,
                    connected = intent.action == BluetoothDevice.ACTION_ACL_CONNECTED,
                ),
            )
        }
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            AutomationAlarms.sync(context)
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
            wifiNetwork = network
            updateWifi(true)
            // The SSID can appear a moment after the connection, so states re-read on every change.
            evaluateStates()
        }

        override fun onLost(network: Network) {
            if (network != wifiNetwork) return
            wifiNetwork = null
            updateWifi(false)
            evaluateStates()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = AutomationStorage.store(this)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_SERVICE,
                getString(R.string.automation_service_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // "Tạm dừng" turns every rule off, so the time alarms and proximity alerts stop too.
            store.disableAll()
            stopServiceNow()
            return START_NOT_STICKY
        }
        startForegroundCompat()
        if (!store.hasEnabledRules()) {
            stopServiceNow()
            return START_NOT_STICKY
        }
        registerReceivers()
        AutomationAlarms.sync(this)
        AutomationLocation.sync(this)
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceivers()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun registerReceivers() {
        if (registered) return
        registered = true
        register(batteryReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        })
        register(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        })
        register(dndReceiver, IntentFilter(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED))
        lastDnd = currentDnd()
        register(modeReceiver, IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(UiModeManager.ACTION_ENTER_CAR_MODE)
            addAction(UiModeManager.ACTION_EXIT_CAR_MODE)
        })
        lastPowerSave = getSystemService(PowerManager::class.java).isPowerSaveMode
        lastCarMode = currentCarMode()
        if (AutomationSnapshot.hasCalendarAccess(this)) {
            runCatching {
                contentResolver.registerContentObserver(CalendarContract.Events.CONTENT_URI, true, calendarObserver)
            }
        }
        register(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        })
        register(clockReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        })
        runCatching {
            getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        }
        lastWifi = currentWifiConnected()
        evaluateStates()
    }

    private fun unregisterReceivers() {
        if (!registered) return
        registered = false
        listOf(batteryReceiver, screenReceiver, dndReceiver, bluetoothReceiver, clockReceiver, modeReceiver).forEach { receiver ->
            runCatching { unregisterReceiver(receiver) }
        }
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback) }
        runCatching { contentResolver.unregisterContentObserver(calendarObserver) }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun register(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(receiver, filter)
        }
    }

    private fun onBatteryChanged(intent: Intent) {
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (lastPlugged == null) lastPlugged = plugged
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale
        val previous = lastPercent
        lastPercent = percent
        if (previous != null && previous != percent) {
            dispatch(TriggerEvent.BatteryChanged(previous, percent))
            evaluateStates()
        }
    }

    private fun onPlugChanged(plugged: Boolean) {
        val previous = lastPlugged
        lastPlugged = plugged
        if (previous != null && previous == plugged) return
        dispatch(TriggerEvent.ChargerChanged(plugged))
        evaluateStates()
    }

    private fun updatePowerSave() {
        val on = getSystemService(PowerManager::class.java).isPowerSaveMode
        if (lastPowerSave == on) return
        lastPowerSave = on
        dispatch(TriggerEvent.PowerSaveChanged(on))
        evaluateStates()
    }

    private fun currentCarMode(): Boolean =
        getSystemService(UiModeManager::class.java).currentModeType == android.content.res.Configuration.UI_MODE_TYPE_CAR

    private fun updateCarMode(connected: Boolean) {
        if (lastCarMode == connected) return
        lastCarMode = connected
        dispatch(TriggerEvent.CarModeChanged(connected))
    }

    private fun evaluateStates() = AutomationRuntime.evaluateStates(this)

    private fun updateWifi(connected: Boolean) {
        if (lastWifi == connected) return
        lastWifi = connected
        dispatch(TriggerEvent.WifiChanged(connected, if (connected) wifiSsid() else null))
    }

    private fun currentDnd(): Boolean {
        val filter = getSystemService(NotificationManager::class.java).currentInterruptionFilter
        return filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }

    private fun updateDnd() {
        val on = currentDnd()
        if (lastDnd == on) return
        lastDnd = on
        dispatch(TriggerEvent.DndChanged(on))
    }

    private fun currentWifiConnected(): Boolean = runCatching {
        val manager = getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return@runCatching false
        val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }.getOrDefault(false)

    private fun wifiSsid(): String? = AutomationSnapshot.wifiSsid(this)

    private fun dispatch(event: TriggerEvent) = AutomationRuntime.dispatch(this, event)

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
            ?: Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName"))
        val open = PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AutomationForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val active = store.rules().count { it.enabled }
        return Notification.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_automation)
            .setContentTitle(getString(R.string.automation_service_title))
            .setContentText(getString(R.string.automation_service_text, active))
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.automation_service_stop), stop).build())
            .build()
    }

    private fun stopServiceNow() {
        unregisterReceivers()
        AutomationAlarms.sync(this)
        AutomationLocation.sync(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val CHANNEL_SERVICE = "automation_service"
        private const val NOTIFICATION_ID = 4
        private const val ACTION_STOP = "vn.hongducdev.honortoolkit.feature.automation.STOP"
        private const val SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"

        /** Starts or stops the runtime so it matches the saved rules and re-registers schedules. */
        fun sync(context: Context) {
            val app = context.applicationContext
            val intent = Intent(app, AutomationForegroundService::class.java)
            if (AutomationStorage.store(app).hasEnabledRules()) {
                runCatching { app.startForegroundService(intent) }
            } else {
                runCatching { app.stopService(intent) }
            }
            AutomationAlarms.sync(app)
            AutomationLocation.sync(app)
        }
    }
}
