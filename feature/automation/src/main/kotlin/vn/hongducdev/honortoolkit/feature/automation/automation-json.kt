package vn.hongducdev.honortoolkit.feature.automation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rule storage format. Bump [VERSION] when a field changes meaning; [decodeRules] keeps reading
 * versions 1 and 2 and drops anything it cannot understand instead of losing the whole list.
 */
object AutomationJson {
    const val VERSION = 2

    private const val KEY_VERSION = "version"
    private const val KEY_RULES = "rules"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRIGGER = "trigger"
    private const val KEY_ACTIONS = "actions"
    private const val KEY_EXIT_ACTIONS = "exitActions"
    private const val KEY_TYPE = "type"

    fun encodeRules(rules: List<AutomationRule>): String {
        val array = JSONArray()
        rules.forEach { array.put(encodeRule(it)) }
        return JSONObject()
            .put(KEY_VERSION, VERSION)
            .put(KEY_RULES, array)
            .toString()
    }

    fun decodeRules(raw: String?): List<AutomationRule> {
        if (raw.isNullOrBlank()) return emptyList()
        val root = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray(KEY_RULES) ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            runCatching { decodeRule(array.getJSONObject(index)) }.getOrNull()
        }
    }

    fun encodeRule(rule: AutomationRule): JSONObject = JSONObject()
        .put(KEY_ID, rule.id)
        .put(KEY_NAME, rule.name)
        .put(KEY_ENABLED, rule.enabled)
        .put(KEY_TRIGGER, encodeTrigger(rule.trigger))
        .put(KEY_ACTIONS, JSONArray().also { array -> rule.actions.forEach { array.put(encodeAction(it)) } })
        .put(KEY_EXIT_ACTIONS, JSONArray().also { array -> rule.exitActions.forEach { array.put(encodeAction(it)) } })

    fun decodeRule(json: JSONObject): AutomationRule {
        val id = json.getString(KEY_ID)
        require(id.isNotBlank()) { "blank rule id" }
        val actions = decodeActions(json.optJSONArray(KEY_ACTIONS))
        val exitActions = decodeActions(json.optJSONArray(KEY_EXIT_ACTIONS))
        return AutomationRule(
            id = id,
            name = json.optString(KEY_NAME),
            enabled = json.optBoolean(KEY_ENABLED, false),
            trigger = decodeTrigger(json.getJSONObject(KEY_TRIGGER)),
            actions = actions,
            exitActions = exitActions,
        )
    }

    private fun decodeActions(array: JSONArray?): List<Action> = array
        ?.let {
            (0 until it.length()).mapNotNull { index ->
                runCatching { decodeAction(it.getJSONObject(index)) }.getOrNull()
            }
        }
        .orEmpty()

    fun encodeTrigger(trigger: Trigger): JSONObject = when (trigger) {
        is Trigger.Charger -> JSONObject().put(KEY_TYPE, "charger").put("plugged", trigger.plugged)
        is Trigger.BatteryLevel -> JSONObject()
            .put(KEY_TYPE, "battery")
            .put("percent", trigger.percent)
            .put("dropping", trigger.dropping)
        Trigger.ScreenOn -> JSONObject().put(KEY_TYPE, "screenOn")
        Trigger.ScreenOff -> JSONObject().put(KEY_TYPE, "screenOff")
        Trigger.DeviceUnlock -> JSONObject().put(KEY_TYPE, "unlock")
        is Trigger.DoNotDisturb -> JSONObject().put(KEY_TYPE, "dnd").put("on", trigger.on)
        is Trigger.Wifi -> JSONObject()
            .put(KEY_TYPE, "wifi")
            .put("connected", trigger.connected)
            .put("ssid", trigger.ssid ?: JSONObject.NULL)
        is Trigger.Bluetooth -> JSONObject()
            .put(KEY_TYPE, "bluetooth")
            .put("address", trigger.address)
            .put("name", trigger.name)
            .put("connected", trigger.connected)
        is Trigger.TimeOfDay -> JSONObject()
            .put(KEY_TYPE, "time")
            .put("hour", trigger.hour)
            .put("minute", trigger.minute)
            .put("days", trigger.days.toJsonArray())
        is Trigger.AppOpened -> JSONObject().put(KEY_TYPE, "appOpened").put("package", trigger.packageName)
        is Trigger.AppClosed -> JSONObject().put(KEY_TYPE, "appClosed").put("package", trigger.packageName)
        is Trigger.Notification -> JSONObject()
            .put(KEY_TYPE, "notification")
            .put("package", trigger.packageName)
            .put("keyword", trigger.keyword ?: JSONObject.NULL)
        is Trigger.LocationRegion -> JSONObject()
            .put(KEY_TYPE, "location")
            .put("latitude", trigger.latitude)
            .put("longitude", trigger.longitude)
            .put("radius", trigger.radiusMeters.toDouble())
            .put("entering", trigger.entering)
        is Trigger.PowerSaving -> JSONObject().put(KEY_TYPE, "powerSaving").put("on", trigger.on)
        is Trigger.AndroidAuto -> JSONObject().put(KEY_TYPE, "androidAuto").put("connected", trigger.connected)
        is Trigger.InState -> JSONObject().put(KEY_TYPE, "state").put("state", encodeState(trigger.state))
    }

    fun encodeState(state: State): JSONObject = when (state) {
        State.Charging -> JSONObject().put(KEY_TYPE, "charging")
        is State.BatteryRange -> JSONObject().put(KEY_TYPE, "batteryRange").put("min", state.min).put("max", state.max)
        State.ScreenOn -> JSONObject().put(KEY_TYPE, "screenOn")
        State.PowerSaving -> JSONObject().put(KEY_TYPE, "powerSaving")
        is State.WifiNetwork -> JSONObject().put(KEY_TYPE, "wifi").put("ssid", state.ssid ?: JSONObject.NULL)
        is State.TimePeriod -> JSONObject()
            .put(KEY_TYPE, "timePeriod")
            .put("start", state.startMinute)
            .put("end", state.endMinute)
            .put("days", state.days.toJsonArray())
        is State.CalendarEvent -> JSONObject().put(KEY_TYPE, "calendar").put("keyword", state.keyword ?: JSONObject.NULL)
    }

    fun decodeState(json: JSONObject): State = when (json.getString(KEY_TYPE)) {
        "charging" -> State.Charging
        "batteryRange" -> State.BatteryRange(json.optInt("min", 0), json.optInt("max", 100))
        "screenOn" -> State.ScreenOn
        "powerSaving" -> State.PowerSaving
        "wifi" -> State.WifiNetwork(json.nullableString("ssid"))
        "timePeriod" -> State.TimePeriod(json.optInt("start"), json.optInt("end"), json.optJSONArray("days").toDays())
        "calendar" -> State.CalendarEvent(json.nullableString("keyword"))
        else -> error("unknown state ${json.optString(KEY_TYPE)}")
    }

    fun decodeTrigger(json: JSONObject): Trigger = when (json.getString(KEY_TYPE)) {
        "charger" -> Trigger.Charger(json.optBoolean("plugged"))
        "battery" -> Trigger.BatteryLevel(json.optInt("percent", 20), json.optBoolean("dropping", true))
        "screenOn" -> Trigger.ScreenOn
        "screenOff" -> Trigger.ScreenOff
        "unlock" -> Trigger.DeviceUnlock
        "dnd" -> Trigger.DoNotDisturb(json.optBoolean("on", true))
        "wifi" -> Trigger.Wifi(json.optBoolean("connected", true), json.nullableString("ssid"))
        "bluetooth" -> Trigger.Bluetooth(
            address = json.optString("address"),
            name = json.optString("name"),
            connected = json.optBoolean("connected", true),
        )
        "time" -> Trigger.TimeOfDay(json.optInt("hour"), json.optInt("minute"), json.optJSONArray("days").toDays())
        "appOpened" -> Trigger.AppOpened(json.optString("package"))
        "appClosed" -> Trigger.AppClosed(json.optString("package"))
        "notification" -> Trigger.Notification(json.optString("package"), json.nullableString("keyword"))
        "location" -> Trigger.LocationRegion(
            latitude = json.optDouble("latitude"),
            longitude = json.optDouble("longitude"),
            radiusMeters = json.optDouble("radius", 100.0).toFloat(),
            entering = json.optBoolean("entering", true),
        )
        "powerSaving" -> Trigger.PowerSaving(json.optBoolean("on", true))
        "androidAuto" -> Trigger.AndroidAuto(json.optBoolean("connected", true))
        "state" -> Trigger.InState(decodeState(json.getJSONObject("state")))
        else -> error("unknown trigger ${json.optString(KEY_TYPE)}")
    }

    fun encodeAction(action: Action): JSONObject = when (action) {
        is Action.WriteSetting -> JSONObject()
            .put(KEY_TYPE, "writeSetting")
            .put("scope", action.scope.cli)
            .put("key", action.key)
            .put("value", action.value)
            .put("advanced", action.advanced)
        is Action.Ringer -> JSONObject().put(KEY_TYPE, "ringer").put("mode", action.mode.name)
        is Action.LaunchApp -> JSONObject().put(KEY_TYPE, "launchApp").put("package", action.packageName)
        is Action.Notify -> JSONObject()
            .put(KEY_TYPE, "notify")
            .put("title", action.title)
            .put("message", action.message)
        is Action.Wait -> JSONObject().put(KEY_TYPE, "wait").put("seconds", action.seconds)
        is Action.Shell -> JSONObject().put(KEY_TYPE, "shell").put("command", action.command)
        is Action.TuneFeature -> JSONObject()
            .put(KEY_TYPE, "tune")
            .put("feature", action.featureId)
            .put("enabled", action.enabled)
        is Action.ToolkitStrip -> JSONObject().put(KEY_TYPE, "strip").put("enabled", action.enabled)
        Action.CircleToSearch -> JSONObject().put(KEY_TYPE, "circle")
        is Action.Flashlight -> JSONObject().put(KEY_TYPE, "flashlight").put("on", action.on)
        is Action.Media -> JSONObject().put(KEY_TYPE, "media").put("button", action.button.name)
        is Action.Volume -> JSONObject()
            .put(KEY_TYPE, "volume")
            .put("stream", action.stream.name)
            .put("percent", action.percent)
        Action.Screenshot -> JSONObject().put(KEY_TYPE, "screenshot")
        Action.ScreenOff -> JSONObject().put(KEY_TYPE, "screenOffAction")
        is Action.Vibrate -> JSONObject().put(KEY_TYPE, "vibrate").put("kind", action.kind.name)
        is Action.DoNotDisturbSwitch -> JSONObject().put(KEY_TYPE, "dndSwitch").put("mode", action.mode.name)
        is Action.MuteMedia -> JSONObject().put(KEY_TYPE, "muteMedia").put("mode", action.mode.name)
        Action.CycleRinger -> JSONObject().put(KEY_TYPE, "cycleRinger")
        Action.ToggleVibrate -> JSONObject().put(KEY_TYPE, "toggleVibrate")
        is Action.LowPower -> JSONObject().put(KEY_TYPE, "lowPower").put("mode", action.mode.name)
        is Action.KeepAwake -> JSONObject()
            .put(KEY_TYPE, "keepAwake")
            .put("mode", action.mode.name)
            .put("minutes", action.minutes)
        is Action.AutoBrightness -> JSONObject().put(KEY_TYPE, "autoBrightness").put("mode", action.mode.name)
        is Action.NetworkSwitch -> JSONObject()
            .put(KEY_TYPE, "network")
            .put("kind", action.kind.name)
            .put("mode", action.mode.name)
        is Action.OpenCamera -> JSONObject().put(KEY_TYPE, "camera").put("kind", action.kind.name)
        is Action.OpenActivity -> JSONObject()
            .put(KEY_TYPE, "openActivity")
            .put("package", action.packageName)
            .put("class", action.className)
        is Action.OpenSettingsPage -> JSONObject().put(KEY_TYPE, "settingsPage").put("page", action.page.name)
        Action.KeyboardPicker -> JSONObject().put(KEY_TYPE, "keyboardPicker")
        Action.VoiceAssistant -> JSONObject().put(KEY_TYPE, "voiceAssistant")
        Action.LikeCurrentSong -> JSONObject().put(KEY_TYPE, "likeSong")
        Action.OpenNowPlaying -> JSONObject().put(KEY_TYPE, "nowPlaying")
        Action.SplitScreen -> JSONObject().put(KEY_TYPE, "splitScreen")
        is Action.FreezeApp -> JSONObject()
            .put(KEY_TYPE, "freeze")
            .put("package", action.packageName)
            .put("frozen", action.frozen)
    }

    fun decodeAction(json: JSONObject): Action = when (json.getString(KEY_TYPE)) {
        "writeSetting" -> Action.WriteSetting(
            scope = SettingScope.entries.firstOrNull { it.cli == json.optString("scope") } ?: SettingScope.Secure,
            key = json.optString("key"),
            value = json.optString("value"),
            advanced = json.optBoolean("advanced", false),
        )
        "ringer" -> Action.Ringer(
            RingerMode.entries.firstOrNull { it.name == json.optString("mode") } ?: RingerMode.Normal,
        )
        "launchApp" -> Action.LaunchApp(json.optString("package"))
        "notify" -> Action.Notify(json.optString("title"), json.optString("message"))
        "wait" -> Action.Wait(json.optInt("seconds", 1))
        "shell" -> Action.Shell(json.optString("command"))
        "tune" -> Action.TuneFeature(json.optString("feature"), json.optBoolean("enabled", true))
        "strip" -> Action.ToolkitStrip(json.optBoolean("enabled", true))
        "circle" -> Action.CircleToSearch
        "flashlight" -> Action.Flashlight(json.optBoolean("on", true))
        "media" -> Action.Media(
            MediaButton.entries.firstOrNull { it.name == json.optString("button") } ?: MediaButton.PlayPause,
        )
        "volume" -> Action.Volume(
            VolumeStream.entries.firstOrNull { it.name == json.optString("stream") } ?: VolumeStream.Media,
            json.optInt("percent", 50),
        )
        "screenshot" -> Action.Screenshot
        "screenOffAction" -> Action.ScreenOff
        "vibrate" -> Action.Vibrate(json.enumOf("kind", HapticKind.Click))
        "dndSwitch" -> Action.DoNotDisturbSwitch(json.enumOf("mode", Toggle.Flip))
        "muteMedia" -> Action.MuteMedia(json.enumOf("mode", Toggle.Flip))
        "cycleRinger" -> Action.CycleRinger
        "toggleVibrate" -> Action.ToggleVibrate
        "lowPower" -> Action.LowPower(json.enumOf("mode", Toggle.Flip))
        "keepAwake" -> Action.KeepAwake(json.enumOf("mode", Toggle.On), json.optInt("minutes", 30))
        "autoBrightness" -> Action.AutoBrightness(json.enumOf("mode", Toggle.Flip))
        "network" -> Action.NetworkSwitch(json.enumOf("kind", NetworkKind.Wifi), json.enumOf("mode", Toggle.Flip))
        "camera" -> Action.OpenCamera(json.enumOf("kind", CameraKind.Photo))
        "openActivity" -> Action.OpenActivity(json.optString("package"), json.optString("class"))
        "settingsPage" -> Action.OpenSettingsPage(json.enumOf("page", SettingsPage.Wifi))
        "keyboardPicker" -> Action.KeyboardPicker
        "voiceAssistant" -> Action.VoiceAssistant
        "likeSong" -> Action.LikeCurrentSong
        "nowPlaying" -> Action.OpenNowPlaying
        "splitScreen" -> Action.SplitScreen
        "freeze" -> Action.FreezeApp(json.optString("package"), json.optBoolean("frozen", true))
        else -> error("unknown action ${json.optString(KEY_TYPE)}")
    }

    private inline fun <reified E : Enum<E>> JSONObject.enumOf(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == optString(key) } ?: default

    private fun Set<Int>.toJsonArray(): JSONArray = JSONArray().also { array -> sorted().forEach(array::put) }

    private fun JSONArray?.toDays(): Set<Int> =
        if (this == null) emptySet() else (0 until length()).map { optInt(it) }.toSet()

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
