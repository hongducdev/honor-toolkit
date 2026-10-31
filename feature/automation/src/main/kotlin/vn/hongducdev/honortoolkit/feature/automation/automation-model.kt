package vn.hongducdev.honortoolkit.feature.automation

/**
 * One rule. [actions] run when the trigger fires, or, for a rule on a [Trigger.InState], when the
 * state is entered; [exitActions] run when the state ends and stay empty for every other trigger.
 */
data class AutomationRule(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val trigger: Trigger,
    val actions: List<Action>,
    val exitActions: List<Action> = emptyList(),
) {
    val isState: Boolean get() = trigger is Trigger.InState

    /** Empty when the rule can run. Messages are shown as-is in the editor. */
    fun problems(): List<String> = buildList {
        if (name.isBlank()) add("Quy tắc chưa có tên")
        if (actions.isEmpty()) add(if (isState) "Quy tắc chưa có hành động khi vào" else "Quy tắc chưa có hành động")
        if (!isState && exitActions.isNotEmpty()) add("Hành động khi ra chỉ dùng cho quy tắc theo trạng thái")
        addAll(triggerProblems(trigger))
        actions.forEachIndexed { index, action -> addAll(action.problems("Hành động ${index + 1}: ")) }
        exitActions.forEachIndexed { index, action -> addAll(action.problems("Hành động khi ra ${index + 1}: ")) }
    }

    val isValid: Boolean get() = problems().isEmpty()

    private fun triggerProblems(trigger: Trigger): List<String> = buildList {
        when (val t = trigger) {
            is Trigger.BatteryLevel -> if (t.percent !in 0..100) add("Mức pin phải trong khoảng 0–100")
            is Trigger.Wifi -> if (t.ssid != null && t.ssid.isBlank()) add("SSID đang trống")
            is Trigger.Bluetooth -> {
                if (t.address.isBlank()) add("Chưa chọn thiết bị Bluetooth")
                if (!t.address.matches(MAC_PATTERN)) add("Địa chỉ Bluetooth không hợp lệ")
            }
            is Trigger.TimeOfDay -> {
                if (t.hour !in 0..23) add("Giờ phải trong khoảng 0–23")
                if (t.minute !in 0..59) add("Phút phải trong khoảng 0–59")
                if (t.days.any { it !in 1..7 }) add("Ngày trong tuần không hợp lệ")
            }
            is Trigger.AppOpened -> if (!t.packageName.isPackageName()) add("Tên gói ứng dụng không hợp lệ")
            is Trigger.AppClosed -> if (!t.packageName.isPackageName()) add("Tên gói ứng dụng không hợp lệ")
            is Trigger.Notification -> {
                if (!t.packageName.isPackageName()) add("Tên gói ứng dụng không hợp lệ")
                if (t.keyword != null && t.keyword.isBlank()) add("Từ khoá đang trống")
            }
            is Trigger.LocationRegion -> {
                if (t.latitude !in -90.0..90.0) add("Vĩ độ phải trong khoảng -90…90")
                if (t.longitude !in -180.0..180.0) add("Kinh độ phải trong khoảng -180…180")
                if (t.radiusMeters !in 1f..100_000f) add("Bán kính phải trong khoảng 1–100000 m")
            }
            is Trigger.InState -> addAll(t.state.problems())
            else -> Unit
        }
    }

    private companion object {
        val MAC_PATTERN = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$")
    }
}

internal fun Action.problems(at: String): List<String> = buildList {
    when (val action = this@problems) {
        is Action.WriteSetting -> {
            if (action.key.isBlank()) add(at + "chưa có khoá")
            if (action.key.any { it.isWhitespace() }) add(at + "khoá không được chứa khoảng trắng")
        }
        is Action.LaunchApp -> if (!action.packageName.isPackageName()) add(at + "tên gói không hợp lệ")
        is Action.Notify -> {
            if (action.title.isBlank()) add(at + "chưa có tiêu đề")
            if (action.message.isBlank()) add(at + "chưa có nội dung")
        }
        is Action.Wait -> if (action.seconds !in 1..3_600) add(at + "thời gian chờ phải trong khoảng 1–3600 giây")
        is Action.Shell -> if (action.command.isBlank()) add(at + "lệnh đang trống")
        is Action.TuneFeature -> if (action.featureId.isBlank()) add(at + "chưa chọn tính năng")
        is Action.Volume -> if (action.percent !in 0..100) add(at + "âm lượng phải trong khoảng 0–100%")
        is Action.KeepAwake -> if (action.minutes !in 1..720) add(at + "thời gian giữ sáng phải trong khoảng 1–720 phút")
        is Action.OpenActivity -> {
            if (!action.packageName.isPackageName()) add(at + "tên gói không hợp lệ")
            if (action.className.isBlank() || action.className.any { it.isWhitespace() }) add(at + "tên lớp không hợp lệ")
        }
        is Action.FreezeApp -> if (!action.packageName.isPackageName()) add(at + "tên gói không hợp lệ")
        else -> Unit
    }
}

internal fun String.isPackageName(): Boolean =
    isNotBlank() && matches(Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$"))

sealed interface ActionResult {
    data object Done : ActionResult

    data class Skipped(val reason: String) : ActionResult

    data class Failed(val reason: String) : ActionResult
}

/** One action and what happened to it. */
data class AutomationStep(val action: Action, val result: ActionResult)

/** One rule and what its actions did for a trigger event. */
data class AutomationOutcome(val ruleId: String, val ruleName: String, val steps: List<AutomationStep>)

/** One line of the user-visible journal. Notification text never reaches this type. */
data class AutomationLogEntry(
    val at: Long,
    val ruleName: String,
    val action: String,
    val result: ActionResult,
)
