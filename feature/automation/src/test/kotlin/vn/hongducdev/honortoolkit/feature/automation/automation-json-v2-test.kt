package vn.hongducdev.honortoolkit.feature.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationJsonV2Test {

    private val everyNewAction = listOf(
        Action.Vibrate(HapticKind.Heavy),
        Action.DoNotDisturbSwitch(Toggle.Flip),
        Action.MuteMedia(Toggle.On),
        Action.CycleRinger,
        Action.ToggleVibrate,
        Action.LowPower(Toggle.Off),
        Action.KeepAwake(Toggle.On, 45),
        Action.AutoBrightness(Toggle.Flip),
        Action.NetworkSwitch(NetworkKind.Wifi, Toggle.Flip),
        Action.NetworkSwitch(NetworkKind.MobileData, Toggle.Off),
        Action.OpenCamera(CameraKind.QrScanner),
        Action.OpenActivity("com.example.app", "com.example.app.MainActivity"),
        Action.OpenSettingsPage(SettingsPage.Battery),
        Action.KeyboardPicker,
        Action.VoiceAssistant,
        Action.LikeCurrentSong,
        Action.OpenNowPlaying,
        Action.SplitScreen,
        Action.FreezeApp("com.example.app", frozen = true),
    )

    private val everyNewTrigger = listOf(
        Trigger.TimeOfDay(7, 30, setOf(1, 3, 5)),
        Trigger.PowerSaving(on = false),
        Trigger.AndroidAuto(connected = true),
        Trigger.InState(State.Charging),
        Trigger.InState(State.BatteryRange(10, 30)),
        Trigger.InState(State.ScreenOn),
        Trigger.InState(State.PowerSaving),
        Trigger.InState(State.TimePeriod(22 * 60, 6 * 60 + 30, setOf(6, 7))),
        Trigger.InState(State.CalendarEvent("họp")),
        Trigger.InState(State.CalendarEvent(null)),
    )

    @Test
    fun newActionsRoundTrip() {
        val rule = AutomationRule("r", "Mới", true, Trigger.ScreenOn, everyNewAction)
        val decoded = AutomationJson.decodeRules(AutomationJson.encodeRules(listOf(rule))).single()
        assertEquals(everyNewAction, decoded.actions)
    }

    @Test
    fun newTriggersAndStatesRoundTrip() {
        everyNewTrigger.forEach { trigger ->
            val rule = AutomationRule("r", "Mới", true, trigger, listOf(Action.Wait(1)))
            val decoded = AutomationJson.decodeRules(AutomationJson.encodeRules(listOf(rule))).single()
            assertEquals(trigger, decoded.trigger)
        }
    }

    @Test
    fun exitActionsRoundTrip() {
        val rule = AutomationRule(
            "r",
            "Ngủ",
            true,
            Trigger.InState(State.TimePeriod(23 * 60, 6 * 60)),
            listOf(Action.DoNotDisturbSwitch(Toggle.On)),
            listOf(Action.DoNotDisturbSwitch(Toggle.Off), Action.Vibrate(HapticKind.Tick)),
        )
        val decoded = AutomationJson.decodeRules(AutomationJson.encodeRules(listOf(rule))).single()
        assertEquals(rule, decoded)
        assertTrue(decoded.isValid)
    }

    @Test
    fun versionOneRulesStillLoad() {
        val raw = """
            {"version":1,"rules":[
              {"id":"a","name":"Giờ","enabled":true,
               "trigger":{"type":"time","hour":8,"minute":15},
               "actions":[{"type":"wait","seconds":3}]}
            ]}
        """.trimIndent()
        val rule = AutomationJson.decodeRules(raw).single()
        assertEquals(Trigger.TimeOfDay(8, 15, emptySet()), rule.trigger)
        assertEquals(emptyList<Action>(), rule.exitActions)
        assertEquals(listOf<Action>(Action.Wait(3)), rule.actions)
    }

    @Test
    fun anUnknownActionIsDroppedWithoutLosingTheRule() {
        val raw = """
            {"version":2,"rules":[
              {"id":"a","name":"X","enabled":true,"trigger":{"type":"screenOn"},
               "actions":[{"type":"fromTheFuture"},{"type":"wait","seconds":2}]}
            ]}
        """.trimIndent()
        val rule = AutomationJson.decodeRules(raw).single()
        assertEquals(listOf<Action>(Action.Wait(2)), rule.actions)
    }

    @Test
    fun actionValidationCatchesBadInput() {
        val rule = AutomationRule(
            "r", "Sai", true, Trigger.ScreenOn,
            listOf(Action.KeepAwake(Toggle.On, 0), Action.OpenActivity("khong hop le", ""), Action.FreezeApp("x", true)),
        )
        assertEquals(4, rule.problems().size)
    }
}
