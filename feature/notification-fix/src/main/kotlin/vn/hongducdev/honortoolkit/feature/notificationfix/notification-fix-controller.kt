package vn.hongducdev.honortoolkit.feature.notificationfix

import vn.hongducdev.honortoolkit.core.adb.AdbShell

enum class FixStep { Notifications, DozeWhitelist, BackgroundRun, ExactAlarm }

enum class StepStatus {
    Satisfied,
    Missing,
    NotApplicable,

    /** The command ran but reading the setting back showed no change. */
    Failed,
}

data class StepState(val step: FixStep, val status: StepStatus)

internal class NotificationFixController(private val shell: AdbShell) {
    /** Third-party packages on user 0, or null when the shell could not list them. */
    suspend fun userPackages(): Set<String>? {
        val result = shell.run("pm list packages -3 --user 0")
        if (!result.isSuccess) return null
        return result.output.lineSequence().map { it.trim().removePrefix("package:") }.filter(::isValidPackageName).toSet()
    }

    /** Null when the shell failed or [packageName] is not a user app. */
    suspend fun inspect(packageName: String): List<StepState>? {
        if (!isAllowed(packageName)) return null
        val dump = shell.run("dumpsys package $packageName")
        val whitelist = shell.run("cmd deviceidle whitelist")
        if (!dump.isSuccess || !whitelist.isSuccess) return null

        val notification = parseNotificationGranted(dump.output)
        val background = BACKGROUND_OPS.map { appOpAllowed(packageName, it) ?: return null }
        val exactAlarm = if (declaresExactAlarm(dump.output)) appOpAllowed(packageName, EXACT_ALARM_OP) ?: return null else null
        return listOf(
            StepState(FixStep.Notifications, notification.toStatus()),
            StepState(FixStep.DozeWhitelist, isDozeWhitelisted(whitelist.output, packageName).toStatus()),
            StepState(FixStep.BackgroundRun, background.all { it }.toStatus()),
            StepState(FixStep.ExactAlarm, exactAlarm.toStatus()),
        )
    }

    /** Applies every step that is [StepStatus.Missing], then re-reads so a ROM that ignores a command shows up as [StepStatus.Failed]. */
    suspend fun apply(packageName: String): List<StepState>? {
        val before = inspect(packageName) ?: return null
        val missing = before.filter { it.status == StepStatus.Missing }.map { it.step }
        for (step in missing) commandsFor(step, packageName).forEach { shell.run(it) }
        val after = inspect(packageName) ?: return null
        return after.map { if (it.step in missing && it.status == StepStatus.Missing) it.copy(status = StepStatus.Failed) else it }
    }

    private fun commandsFor(step: FixStep, packageName: String): List<String> = when (step) {
        FixStep.Notifications -> listOf("pm grant --user 0 $packageName android.permission.POST_NOTIFICATIONS")
        FixStep.DozeWhitelist -> listOf("cmd deviceidle whitelist +$packageName")
        FixStep.BackgroundRun -> BACKGROUND_OPS.map { "cmd appops set --user 0 $packageName $it allow" }
        FixStep.ExactAlarm -> listOf("cmd appops set --user 0 $packageName $EXACT_ALARM_OP allow")
    }

    private suspend fun appOpAllowed(packageName: String, op: String): Boolean? {
        val result = shell.run("cmd appops get --user 0 $packageName $op")
        return if (result.isSuccess) parseAppOpAllowed(result.output) ?: false else null
    }

    private suspend fun isAllowed(packageName: String): Boolean = isValidPackageName(packageName) && packageName in (userPackages() ?: emptySet())

    private fun Boolean?.toStatus(): StepStatus = when (this) {
        null -> StepStatus.NotApplicable
        true -> StepStatus.Satisfied
        false -> StepStatus.Missing
    }

    private companion object {
        val BACKGROUND_OPS = listOf("RUN_ANY_IN_BACKGROUND", "RUN_IN_BACKGROUND")
        const val EXACT_ALARM_OP = "SCHEDULE_EXACT_ALARM"
    }
}
