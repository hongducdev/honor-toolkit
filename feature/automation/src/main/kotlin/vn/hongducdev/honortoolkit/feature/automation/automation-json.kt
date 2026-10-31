package vn.hongducdev.honortoolkit.feature.automation

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rule storage format. Bump [VERSION] when a field changes meaning; [decodeRules] keeps reading
 * version 1 and drops anything it cannot understand instead of losing the whole list.
 */
object AutomationJson {
    const val VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_RULES = "rules"
    private const val KEY_ID = "id"
    private const val KEY_NAME = "name"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TRIGGER = "trigger"
    private const val KEY_ACTIONS = "actions"
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

    fun decodeRule(json: JSONObject): AutomationRule {
        val id = json.getString(KEY_ID)
        require(id.isNotBlank()) { "blank rule id" }
        val actions = json.optJSONArray(KEY_ACTIONS)
            ?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    runCatching { decodeAction(array.getJSONObject(index)) }.getOrNull()
                }
            }
            .orEmpty()
        return AutomationRule(
            id = id,
            name = json.optString(KEY_NAME),
            enabled = json.optBoolean(KEY_ENABLED, false),
            trigger = decodeTrigger(json.getJSONObject(KEY_TRIGGER)),
            actions = actions,
        )
    }

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
        "time" -> Trigger.TimeOfDay(json.optInt("hour"), json.optInt("minute"))
        "appOpened" -> Trigger.AppOpened(json.optString("package"))
        "appClosed" -> Trigger.AppClosed(json.optString("package"))
        "notification" -> Trigger.Notification(json.optString("package"), json.nullableString("keyword"))
        "location" -> Trigger.LocationRegion(
            latitude = json.optDouble("latitude"),
            longitude = json.optDouble("longitude"),
            radiusMeters = json.optDouble("radius", 100.0).toFloat(),
            entering = json.optBoolean("entering", true),
        )
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
        else -> error("unknown action ${json.optString(KEY_TYPE)}")
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }
}
