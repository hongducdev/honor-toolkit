package vn.hongducdev.honortoolkit.feature.automation

import android.app.AlarmManager
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/**
 * One-shot alarms for [Trigger.TimeOfDay]. The service reschedules after every run, so a missed
 * alarm does not leave the rule dead. Exact alarms are used when the ROM allows them
 * (`USE_EXACT_ALARM`); otherwise the alarm is inexact and the editor still shows the rule, but the
 * page warns that the time can drift.
 */
object AutomationAlarms {
    private const val ACTION_FIRE = "vn.hongducdev.honortoolkit.feature.automation.TIME_TRIGGER"
    internal const val EXTRA_RULE = "rule_id"

    // canScheduleExactAlarms() is checked before setExactAndAllowWhileIdle; lint cannot see it.
    @SuppressLint("MissingPermission")
    @Synchronized
    fun sync(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java)
        val schedule = AutomationScheduleStore(app)
        schedule.ids(AutomationScheduleStore.Kind.Alarms).forEach { ruleId ->
            manager.cancel(pendingIntent(app, ruleId))
        }
        schedule.save(AutomationScheduleStore.Kind.Alarms, emptySet())

        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        val next = mutableSetOf<String>()
        AutomationStorage.store(app).rules()
            .filter { it.enabled && it.isValid }
            .forEach { rule ->
                val trigger = rule.trigger as? Trigger.TimeOfDay ?: return@forEach
                val at = nextOccurrence(trigger.hour, trigger.minute)
                val intent = pendingIntent(app, rule.id)
                val scheduled = runCatching {
                    if (exact) {
                        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                    } else {
                        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
                    }
                }.isSuccess
                if (scheduled) next += rule.id
            }
        schedule.save(AutomationScheduleStore.Kind.Alarms, next)
    }

    /** Next time today with that hour/minute, or tomorrow when it already passed. */
    fun nextOccurrence(hour: Int, minute: Int, now: Long = System.currentTimeMillis()): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (calendar.timeInMillis <= now) calendar.add(Calendar.DAY_OF_YEAR, 1)
        return calendar.timeInMillis
    }

    fun canScheduleExact(context: Context): Boolean {
        val manager = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
    }

    private fun pendingIntent(context: Context, ruleId: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            Intent(context, AutomationAlarmReceiver::class.java)
                .setAction(ACTION_FIRE)
                .putExtra(EXTRA_RULE, ruleId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

/** Receives one rule's time alarm, reschedules the next one, then runs the rule. */
class AutomationAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ruleId = intent.getStringExtra(AutomationAlarms.EXTRA_RULE) ?: return
        AutomationAlarms.sync(context)
        val rule = AutomationStorage.store(context).rule(ruleId) ?: return
        if (!rule.enabled) return
        val trigger = rule.trigger as? Trigger.TimeOfDay ?: return
        // goAsync keeps the broadcast alive while the actions run; onReceive may return first otherwise.
        val pending = goAsync()
        AutomationRuntime.dispatch(
            context,
            TriggerEvent.TimeReached(trigger.hour, trigger.minute, ruleId),
            onDone = pending::finish,
        )
    }
}
