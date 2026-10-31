package vn.hongducdev.honortoolkit.feature.automation

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager

/**
 * Proximity alerts built into the framework, so no Play Services and no geofence API are needed.
 * Alerts are re-registered whenever rules change or the phone reboots. `addProximityAlert` is
 * best-effort: a ROM that throttles location in the background can drop the alert, so the page does
 * not promise that a region rule always fires (verify on the phone).
 */
object AutomationLocation {
    private const val ACTION_ALERT = "vn.hongducdev.honortoolkit.feature.automation.LOCATION_TRIGGER"
    internal const val EXTRA_RULE = "rule_id"

    // hasPermission() is checked below; lint cannot follow the check through the helper.
    @SuppressLint("MissingPermission")
    @Synchronized
    fun sync(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(LocationManager::class.java)
        val schedule = AutomationScheduleStore(app)
        schedule.ids(AutomationScheduleStore.Kind.Locations).forEach { ruleId ->
            runCatching { manager.removeProximityAlert(pendingIntent(app, ruleId)) }
        }
        schedule.save(AutomationScheduleStore.Kind.Locations, emptySet())
        if (!hasPermission(app)) return

        val registered = mutableSetOf<String>()
        AutomationStorage.store(app).rules()
            .filter { it.enabled && it.isValid }
            .forEach { rule ->
                val trigger = rule.trigger as? Trigger.LocationRegion ?: return@forEach
                val added = runCatching {
                    manager.addProximityAlert(
                        trigger.latitude,
                        trigger.longitude,
                        trigger.radiusMeters,
                        -1L,
                        pendingIntent(app, rule.id),
                    )
                }.isSuccess
                if (added) registered += rule.id
            }
        schedule.save(AutomationScheduleStore.Kind.Locations, registered)
    }

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context, ruleId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            Intent(context, AutomationLocationReceiver::class.java)
                .setAction(ACTION_ALERT)
                .putExtra(EXTRA_RULE, ruleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Receives a proximity transition for one rule and hands it to the engine. */
class AutomationLocationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(AutomationLocation.EXTRA_RULE) ?: return
        val entering = intent.getBooleanExtra(LocationManager.KEY_PROXIMITY_ENTERING, false)
        val pending = goAsync()
        AutomationRuntime.dispatch(context, TriggerEvent.LocationChanged(entering, ruleId), onDone = pending::finish)
    }
}
