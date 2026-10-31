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
 * One-shot alarms for [Trigger.TimeOfDay] and for the boundaries of time-period and calendar states. The service reschedules after every run, so a missed
 * alarm does not leave the rule dead. Exact alarms are used when the ROM allows them
 * (`USE_EXACT_ALARM`); otherwise the alarm is inexact and the editor still shows the rule, but the
 * page warns that the time can drift.
 */
object AutomationAlarms {
    private const val ACTION_FIRE = "vn.hongducdev.honortoolkit.feature.automation.TIME_TRIGGER"
    internal const val EXTRA_RULE = "rule_id"
    private const val CALENDAR_RECHECK_MS = 60L * 60 * 1000

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
                val at = nextFire(app, rule.trigger) ?: return@forEach
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

    /**
     * When a trigger's alarm should ring next, or null when it needs none. A time-period or calendar
     * state wakes at its boundaries only to re-read the phone, so an early or spare wake-up is harmless.
     */
    private fun nextFire(context: Context, trigger: Trigger): Long? = when (trigger) {
        is Trigger.TimeOfDay -> nextOccurrence(trigger.hour, trigger.minute, days = trigger.days)
        is Trigger.InState -> when (val state = trigger.state) {
            is State.TimePeriod -> minOf(
                nextOccurrence(state.startMinute / 60, state.startMinute % 60),
                nextOccurrence(state.endMinute / 60, state.endMinute % 60),
            )
            is State.CalendarEvent -> {
                val spare = System.currentTimeMillis() + CALENDAR_RECHECK_MS
                minOf(AutomationSnapshot.nextCalendarBoundary(context) ?: spare, spare)
            }
            else -> null
        }
        else -> null
    }

    /**
     * The next time at [hour]:[minute] after [now] on one of [days] (ISO numbers, empty = every day).
     * The exact minute counts as already passed.
     */
    fun nextOccurrence(
        hour: Int,
        minute: Int,
        now: Long = System.currentTimeMillis(),
        days: Set<Int> = emptySet(),
    ): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (calendar.timeInMillis > now && days.includes(AutomationSnapshot.isoDay(calendar))) return calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
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
        // goAsync keeps the broadcast alive while the actions run; onReceive may return first otherwise.
        when (val trigger = rule.trigger) {
            is Trigger.TimeOfDay -> {
                val pending = goAsync()
                AutomationRuntime.dispatch(
                    context,
                    TriggerEvent.TimeReached(trigger.hour, trigger.minute, ruleId),
                    onDone = pending::finish,
                )
            }
            is Trigger.InState -> {
                val pending = goAsync()
                AutomationRuntime.evaluateStates(context, onDone = pending::finish)
            }
            else -> Unit
        }
    }
}
