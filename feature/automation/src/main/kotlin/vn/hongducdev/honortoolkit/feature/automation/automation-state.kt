package vn.hongducdev.honortoolkit.feature.automation

/**
 * A condition that holds for a while. A rule on a state runs its entry actions when the condition
 * becomes true and its exit actions when it becomes false again.
 */
sealed interface State {
    data object Charging : State

    /** Active while the level is inside [min]..[max] inclusive. */
    data class BatteryRange(val min: Int, val max: Int) : State

    data object ScreenOn : State

    data object PowerSaving : State

    /** Active while connected to Wi-Fi, optionally only to the network named [ssid]. */
    data class WifiNetwork(val ssid: String? = null) : State

    /** [startMinute] and [endMinute] are minutes since midnight; the period may cross midnight. */
    data class TimePeriod(val startMinute: Int, val endMinute: Int, val days: Set<Int> = emptySet()) : State

    /** Active while a calendar event is in progress, optionally only one whose title has [keyword]. */
    data class CalendarEvent(val keyword: String? = null) : State
}

/** What the phone looks like right now, as far as states care. Event titles are never stored. */
data class DeviceSnapshot(
    val charging: Boolean,
    val batteryPercent: Int,
    val screenOn: Boolean,
    val powerSaving: Boolean,
    val minuteOfDay: Int,
    /** ISO day of week: 1 = Monday … 7 = Sunday. */
    val isoDay: Int,
    val calendarTitles: List<String> = emptyList(),
    val wifiConnected: Boolean = false,
    /** Null while disconnected, or while Android hides the name because location is not granted. */
    val wifiSsid: String? = null,
)

/** An empty set means every day. */
internal fun Set<Int>.includes(isoDay: Int): Boolean = isEmpty() || isoDay in this

internal fun previousIsoDay(isoDay: Int): Int = if (isoDay == 1) 7 else isoDay - 1

fun State.isActive(snapshot: DeviceSnapshot): Boolean = when (this) {
    State.Charging -> snapshot.charging
    is State.BatteryRange -> snapshot.batteryPercent in min..max
    State.ScreenOn -> snapshot.screenOn
    State.PowerSaving -> snapshot.powerSaving
    is State.WifiNetwork -> snapshot.wifiConnected && (ssid.isNullOrBlank() || snapshot.wifiSsid.equals(ssid, ignoreCase = true))
    is State.TimePeriod -> {
        val now = snapshot.minuteOfDay
        when {
            startMinute == endMinute -> false
            startMinute < endMinute -> now in startMinute until endMinute && days.includes(snapshot.isoDay)
            else ->
                (now >= startMinute && days.includes(snapshot.isoDay)) ||
                    (now < endMinute && days.includes(previousIsoDay(snapshot.isoDay)))
        }
    }
    is State.CalendarEvent -> snapshot.calendarTitles.any { title ->
        keyword.isNullOrBlank() || title.contains(keyword, ignoreCase = true)
    }
}

fun State.problems(): List<String> = buildList {
    when (val s = this@problems) {
        is State.BatteryRange -> {
            if (s.min !in 0..100 || s.max !in 0..100) add("Mức pin phải trong khoảng 0–100")
            if (s.min > s.max) add("Mức pin thấp nhất lớn hơn mức cao nhất")
        }
        is State.TimePeriod -> {
            if (s.startMinute !in 0..1_439 || s.endMinute !in 0..1_439) add("Khoảng giờ không hợp lệ")
            if (s.startMinute == s.endMinute) add("Giờ bắt đầu và kết thúc đang trùng nhau")
            if (s.days.any { it !in 1..7 }) add("Ngày trong tuần không hợp lệ")
        }
        is State.CalendarEvent -> if (s.keyword != null && s.keyword.isBlank()) add("Từ khoá đang trống")
        is State.WifiNetwork -> if (s.ssid != null && s.ssid.isBlank()) add("SSID đang trống")
        else -> Unit
    }
}

fun State.summary(): String = when (this) {
    State.Charging -> "Khi đang sạc"
    is State.BatteryRange -> "Khi pin từ $min% đến $max%"
    State.ScreenOn -> "Khi màn hình sáng"
    State.PowerSaving -> "Khi bật tiết kiệm pin"
    is State.WifiNetwork -> if (ssid.isNullOrBlank()) "Khi đang kết nối Wi-Fi" else "Khi đang kết nối Wi-Fi $ssid"
    is State.TimePeriod -> "Từ %02d:%02d đến %02d:%02d%s".format(
        startMinute / 60, startMinute % 60, endMinute / 60, endMinute % 60, daysSuffix(days),
    )
    is State.CalendarEvent -> if (keyword.isNullOrBlank()) "Khi có sự kiện lịch" else "Khi có sự kiện lịch chứa “$keyword”"
}

internal fun daysSuffix(days: Set<Int>): String {
    if (days.isEmpty() || days.size == 7) return ""
    val names = mapOf(1 to "T2", 2 to "T3", 3 to "T4", 4 to "T5", 5 to "T6", 6 to "T7", 7 to "CN")
    return " (" + days.sorted().joinToString(", ") { names.getValue(it) } + ")"
}
