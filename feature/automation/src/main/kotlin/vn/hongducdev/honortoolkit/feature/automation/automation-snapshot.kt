package vn.hongducdev.honortoolkit.feature.automation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.PowerManager
import android.os.UserManager
import android.provider.CalendarContract
import java.util.Calendar

/** Reads the facts that [State]s are built from. Nothing here is stored. */
internal object AutomationSnapshot {
    /** Calendar rows are read only when a state rule needs them and the user has granted access. */
    fun read(context: Context, withCalendar: Boolean, now: Long = System.currentTimeMillis()): DeviceSnapshot {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val power = context.getSystemService(PowerManager::class.java)
        val clock = Calendar.getInstance().apply { timeInMillis = now }
        return DeviceSnapshot(
            charging = (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0,
            batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else 100,
            screenOn = power.isInteractive,
            powerSaving = power.isPowerSaveMode,
            minuteOfDay = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE),
            isoDay = isoDay(clock),
            calendarTitles = if (withCalendar) currentEventTitles(context, now) else emptyList(),
            wifiConnected = wifiConnected(context),
            wifiSsid = if (wifiConnected(context)) wifiSsid(context) else null,
        )
    }

    fun wifiConnected(context: Context): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork ?: return@runCatching false)
        capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }.getOrDefault(false)

    /** Null until location is granted: Android hides the SSID from apps without it. */
    fun wifiSsid(context: Context): String? {
        if (!AutomationLocation.hasPermission(context)) return null
        val info = runCatching { context.getSystemService(WifiManager::class.java).connectionInfo }.getOrNull() ?: return null
        val ssid = runCatching { info.ssid }.getOrNull() ?: return null
        if (ssid.isBlank() || ssid == WifiManager.UNKNOWN_SSID) return null
        return ssid.removeSurrounding("\"")
    }

    fun isoDay(calendar: Calendar): Int = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1

    fun hasCalendarAccess(context: Context): Boolean =
        context.getSystemService(UserManager::class.java).isUserUnlocked &&
            context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun currentEventTitles(context: Context, now: Long): List<String> {
        if (!hasCalendarAccess(context)) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(now.toString())
            .build()
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.ALL_DAY),
                null,
                null,
                null,
            )?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        if (cursor.getInt(1) == 0) add(cursor.getString(0).orEmpty())
                    }
                }
            }.orEmpty()
        }.getOrDefault(emptyList())
    }

    /** The next time (after [now], within a day) a calendar event starts or ends; null when none. */
    fun nextCalendarBoundary(context: Context, now: Long = System.currentTimeMillis()): Long? {
        if (!hasCalendarAccess(context)) return null
        val end = now + DAY_MS
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString())
            .appendPath(end.toString())
            .build()
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY),
                null,
                null,
                null,
            )?.use { cursor ->
                var best: Long? = null
                while (cursor.moveToNext()) {
                    if (cursor.getInt(2) != 0) continue
                    for (at in listOf(cursor.getLong(0), cursor.getLong(1))) {
                        if (at > now && (best == null || at < best)) best = at
                    }
                }
                best
            }
        }.getOrNull()
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
