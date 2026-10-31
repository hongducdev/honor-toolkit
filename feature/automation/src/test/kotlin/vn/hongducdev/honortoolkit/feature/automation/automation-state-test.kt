package vn.hongducdev.honortoolkit.feature.automation

import java.util.Calendar
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationStateTest {

    private fun snapshot(
        charging: Boolean = false,
        percent: Int = 50,
        screenOn: Boolean = true,
        powerSaving: Boolean = false,
        minute: Int = 12 * 60,
        day: Int = 3,
        events: List<String> = emptyList(),
    ) = DeviceSnapshot(charging, percent, screenOn, powerSaving, minute, day, events)

    @Test
    fun simpleStatesFollowTheSnapshot() {
        assertTrue(State.Charging.isActive(snapshot(charging = true)))
        assertFalse(State.Charging.isActive(snapshot()))
        assertTrue(State.ScreenOn.isActive(snapshot(screenOn = true)))
        assertTrue(State.PowerSaving.isActive(snapshot(powerSaving = true)))
    }

    @Test
    fun batteryRangeIsInclusive() {
        val state = State.BatteryRange(20, 40)
        assertTrue(state.isActive(snapshot(percent = 20)))
        assertTrue(state.isActive(snapshot(percent = 40)))
        assertFalse(state.isActive(snapshot(percent = 41)))
        assertFalse(state.isActive(snapshot(percent = 19)))
    }

    @Test
    fun timePeriodWithinOneDay() {
        val state = State.TimePeriod(9 * 60, 17 * 60)
        assertTrue(state.isActive(snapshot(minute = 9 * 60)))
        assertFalse(state.isActive(snapshot(minute = 17 * 60)))
    }

    @Test
    fun timePeriodAcrossMidnightUsesTheStartDayForTheEvening() {
        val state = State.TimePeriod(22 * 60, 7 * 60, days = setOf(5))
        assertTrue(state.isActive(snapshot(minute = 23 * 60, day = 5)))
        assertFalse(state.isActive(snapshot(minute = 23 * 60, day = 6)))
        // 02:00 on Saturday still belongs to the period that began on Friday.
        assertTrue(state.isActive(snapshot(minute = 2 * 60, day = 6)))
        assertFalse(state.isActive(snapshot(minute = 2 * 60, day = 5)))
    }

    @Test
    fun calendarStateMatchesKeywordIgnoringCase() {
        val events = listOf("Họp nhóm")
        assertTrue(State.CalendarEvent().isActive(snapshot(events = events)))
        assertTrue(State.CalendarEvent("HỌP").isActive(snapshot(events = events)))
        assertFalse(State.CalendarEvent("ăn trưa").isActive(snapshot(events = events)))
        assertFalse(State.CalendarEvent().isActive(snapshot()))
    }

    @Test
    fun invalidStatesAreReported() {
        assertTrue(State.BatteryRange(50, 10).problems().isNotEmpty())
        assertTrue(State.TimePeriod(60, 60).problems().isNotEmpty())
        assertTrue(State.TimePeriod(60, 120, setOf(9)).problems().isNotEmpty())
        assertTrue(State.CalendarEvent(" ").problems().isNotEmpty())
        assertTrue(State.Charging.problems().isEmpty())
    }

    @Test
    fun nextOccurrenceSkipsDaysThatAreNotSelected() {
        val monday = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 2, 10, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val next = AutomationAlarms.nextOccurrence(7, 30, now = monday, days = setOf(5))
        val result = Calendar.getInstance().apply { timeInMillis = next }
        assertEquals(Calendar.FRIDAY, result.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, result.get(Calendar.HOUR_OF_DAY))
        assertEquals(30, result.get(Calendar.MINUTE))
    }
}

class AutomationStateEngineTest {

    private class Recorder : ActionExecutor {
        val actions = mutableListOf<Action>()

        override suspend fun run(action: Action): ActionResult {
            actions += action
            return ActionResult.Done
        }
    }

    private val enter = Action.Notify("vào", "x")
    private val exit = Action.Notify("ra", "x")

    private fun stateRule(enabled: Boolean = true, exitActions: List<Action> = listOf(exit)) = AutomationRule(
        id = "s1",
        name = "Đang sạc",
        enabled = enabled,
        trigger = Trigger.InState(State.Charging),
        actions = listOf(enter),
        exitActions = exitActions,
    )

    private fun snapshot(charging: Boolean) = DeviceSnapshot(charging, 50, true, false, 600, 1)

    @Test
    fun entersAndLeavesOnTransitions() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine({ listOf(stateRule()) }, recorder)

        engine.evaluate(snapshot(charging = false))
        assertEquals(emptyList<Action>(), recorder.actions)

        engine.evaluate(snapshot(charging = true))
        assertEquals(listOf<Action>(enter), recorder.actions)

        engine.evaluate(snapshot(charging = true))
        assertEquals("no change, no rerun", 1, recorder.actions.size)

        engine.evaluate(snapshot(charging = false))
        assertEquals(listOf<Action>(enter, exit), recorder.actions)
    }

    @Test
    fun appliesEntryWhenFirstSeenAlreadyActive() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine({ listOf(stateRule()) }, recorder)

        engine.evaluate(snapshot(charging = true))

        assertEquals(listOf<Action>(enter), recorder.actions)
    }

    @Test
    fun neverRunsExitForAStateThatWasNeverSeenActive() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine({ listOf(stateRule()) }, recorder)

        engine.evaluate(snapshot(charging = false))
        engine.evaluate(snapshot(charging = false))

        assertTrue(recorder.actions.isEmpty())
    }

    @Test
    fun disabledStateRulesDoNothingAndForgetTheirState() = runBlocking {
        val recorder = Recorder()
        var rules = listOf(stateRule(enabled = true))
        val engine = AutomationEngine({ rules }, recorder)

        engine.evaluate(snapshot(charging = true))
        rules = listOf(stateRule(enabled = false))
        engine.evaluate(snapshot(charging = false))
        assertEquals(listOf<Action>(enter), recorder.actions)
    }

    @Test
    fun eventsNeverTriggerStateRules() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine({ listOf(stateRule()) }, recorder)

        assertEquals(0, engine.dispatch(TriggerEvent.ChargerChanged(true)).size)
        assertTrue(recorder.actions.isEmpty())
    }

    @Test
    fun runNowIgnoresEnabledFlag() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine({ emptyList() }, recorder)

        engine.runNow(stateRule(enabled = false))

        assertEquals(listOf<Action>(enter), recorder.actions)
    }

    @Test
    fun exitActionsAreRejectedOnPlainTriggerRules() {
        val rule = AutomationRule("r", "Sạc", true, Trigger.Charger(true), listOf(enter), listOf(exit))
        assertFalse(rule.isValid)
    }
}

class AutomationWifiStateTest {

    private class Recorder : ActionExecutor {
        val actions = mutableListOf<Action>()

        override suspend fun run(action: Action): ActionResult {
            actions += action
            return ActionResult.Done
        }
    }

    private fun wifi(ssid: String?) = DeviceSnapshot(
        false, 50, false, false, 600, 1, wifiConnected = ssid != null, wifiSsid = ssid,
    )

    @Test
    fun matchesTheNamedNetworkIgnoringCase() {
        val state = State.WifiNetwork("PixelArt 2")
        assertTrue(state.isActive(wifi("pixelart 2")))
        assertFalse(state.isActive(wifi("Nhà khác")))
        assertFalse(state.isActive(wifi(null)))
        assertTrue(State.WifiNetwork().isActive(wifi("bất kỳ")))
    }

    @Test
    fun homeWifiRuleSilencesOnJoinAndRestoresOnLeave() = runBlocking {
        val enter = listOf(Action.Ringer(RingerMode.Vibrate), Action.Volume(VolumeStream.Media, 0))
        val exit = listOf(Action.Ringer(RingerMode.Normal), Action.Volume(VolumeStream.Media, 50))
        val rule = AutomationRule("w", "Ở PixelArt 2", true, Trigger.InState(State.WifiNetwork("PixelArt 2")), enter, exit)
        assertTrue(rule.isValid)
        val recorder = Recorder()
        val engine = AutomationEngine({ listOf(rule) }, recorder)

        engine.evaluate(wifi(null))
        engine.evaluate(wifi("PixelArt 2"))
        assertEquals(enter, recorder.actions)

        engine.evaluate(wifi(null))
        assertEquals(enter + exit, recorder.actions)
    }
}
