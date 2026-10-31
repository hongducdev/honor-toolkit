package vn.hongducdev.honortoolkit.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationJsonTest {

    private val allTriggers = listOf(
        Trigger.Charger(plugged = true),
        Trigger.Charger(plugged = false),
        Trigger.BatteryLevel(percent = 15, dropping = true),
        Trigger.BatteryLevel(percent = 80, dropping = false),
        Trigger.ScreenOn,
        Trigger.ScreenOff,
        Trigger.DeviceUnlock,
        Trigger.DoNotDisturb(on = true),
        Trigger.DoNotDisturb(on = false),
        Trigger.Wifi(connected = true, ssid = "Nha"),
        Trigger.Wifi(connected = false, ssid = null),
        Trigger.Bluetooth(address = "AA:BB:CC:DD:EE:FF", name = "Tai nghe", connected = true),
        Trigger.TimeOfDay(hour = 7, minute = 30),
        Trigger.AppOpened(packageName = "com.android.chrome"),
        Trigger.AppClosed(packageName = "com.android.chrome"),
        Trigger.Notification(packageName = "com.android.mms", keyword = "OTP"),
        Trigger.Notification(packageName = "com.android.mms", keyword = null),
        Trigger.LocationRegion(latitude = 10.7769, longitude = 106.7009, radiusMeters = 250f, entering = true),
    )

    private val allActions = listOf(
        Action.WriteSetting(SettingScope.Secure, "hnnotification_classification_state", "0"),
        Action.WriteSetting(SettingScope.Global, "heads_up_notifications_enabled", "1", advanced = true),
        Action.Ringer(RingerMode.Silent),
        Action.LaunchApp("com.android.chrome"),
        Action.Notify("Pin yếu", "Cắm sạc"),
        Action.Wait(seconds = 12),
        Action.Shell("settings get global adb_enabled"),
        Action.TuneFeature(featureId = "GentleNotifications", enabled = false),
        Action.ToolkitStrip(enabled = true),
        Action.CircleToSearch,
        Action.Flashlight(on = false),
        Action.Media(MediaButton.Next),
        Action.Volume(VolumeStream.Ring, percent = 30),
        Action.Screenshot,
        Action.ScreenOff,
    )

    @Test
    fun everyTriggerAndActionSurvivesARoundTrip() {
        val rules = allTriggers.mapIndexed { index, trigger ->
            AutomationRule(
                id = "rule-$index",
                name = "Quy tắc $index",
                enabled = index % 2 == 0,
                trigger = trigger,
                actions = allActions,
            )
        }

        val decoded = AutomationJson.decodeRules(AutomationJson.encodeRules(rules))

        assertEquals(rules, decoded)
    }

    @Test
    fun oneBrokenRuleDoesNotHideTheRest() {
        val good = AutomationRule("a", "Tốt", true, Trigger.ScreenOn, listOf(Action.CircleToSearch))
        val raw = """
            {"version":1,"rules":[
              {"id":"a","name":"Tốt","enabled":true,"trigger":{"type":"screenOn"},"actions":[]},
              {"id":"b","name":"Lạ","enabled":true,"trigger":{"type":"teleport"},"actions":[]}
            ]}
        """.trimIndent()

        val decoded = AutomationJson.decodeRules(raw)

        assertEquals(1, decoded.size)
        assertEquals(good.id, decoded.single().id)
        assertEquals(good.trigger, decoded.single().trigger)
    }

    @Test
    fun emptyAndBrokenStorageReadAsNoRules() {
        assertEquals(emptyList<AutomationRule>(), AutomationJson.decodeRules(null))
        assertEquals(emptyList<AutomationRule>(), AutomationJson.decodeRules(""))
        assertEquals(emptyList<AutomationRule>(), AutomationJson.decodeRules("not json"))
        assertEquals(emptyList<AutomationRule>(), AutomationJson.decodeRules("""{"rules":"nope"}"""))
    }

    @Test
    fun unknownActionIsDroppedButTheRuleIsKept() {
        val raw = """
            {"version":1,"rules":[
              {"id":"a","name":"Một","enabled":true,"trigger":{"type":"screenOff"},
               "actions":[{"type":"circle"},{"type":"teleport"}]}
            ]}
        """.trimIndent()

        val rule = AutomationJson.decodeRules(raw).single()

        assertEquals(listOf<Action>(Action.CircleToSearch), rule.actions)
    }

    @Test
    fun encodedDocumentCarriesTheSchemaVersion() {
        val encoded = AutomationJson.encodeRules(emptyList())

        assertTrue(encoded.contains("\"version\":${AutomationJson.VERSION}"))
        assertTrue(encoded.contains("\"rules\":[]"))
    }
}
