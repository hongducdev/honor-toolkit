package vn.hongducdev.honortoolkit.feature.automation

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationEngineTest {

    private class Recorder(private val result: ActionResult = ActionResult.Done) : ActionExecutor {
        val actions = mutableListOf<Action>()

        override suspend fun run(action: Action): ActionResult {
            actions += action
            return result
        }
    }

    private fun rule(
        id: String = "r1",
        enabled: Boolean = true,
        trigger: Trigger = Trigger.Charger(true),
        actions: List<Action> = listOf(Action.Wait(1)),
        name: String = "Quy tắc",
    ) = AutomationRule(id, name, enabled, trigger, actions)

    @Test
    fun runsActionsInOrderForTheMatchingTrigger() = runBlocking {
        val recorder = Recorder()
        val actions = listOf(Action.Wait(1), Action.Notify("a", "b"), Action.CircleToSearch)
        val engine = AutomationEngine({ listOf(rule(actions = actions)) }, recorder)

        val outcomes = engine.dispatch(TriggerEvent.ChargerChanged(true))

        assertEquals(1, outcomes.size)
        assertEquals(actions, recorder.actions)
        assertEquals(3, outcomes.single().steps.size)
        assertTrue(outcomes.single().steps.all { it.result == ActionResult.Done })
    }

    @Test
    fun ignoresDisabledRulesAndOtherEvents() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine(
            {
                listOf(
                    rule(id = "off", enabled = false),
                    rule(id = "screen", trigger = Trigger.ScreenOn),
                )
            },
            recorder,
        )

        assertEquals(0, engine.dispatch(TriggerEvent.ChargerChanged(true)).size)
        assertEquals(0, recorder.actions.size)
    }

    @Test
    fun doesNotQueueWhenTheSameRuleIsStillRunning() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Action>()
        val engine = AutomationEngine(
            rules = { listOf(rule(actions = listOf(Action.Wait(10)))) },
            executor = ActionExecutor {
                calls += it
                started.complete(Unit)
                release.await()
                ActionResult.Done
            },
        )

        val job = launch(Dispatchers.Default) { engine.dispatch(TriggerEvent.ChargerChanged(true)) }
        started.await()
        val second = engine.dispatch(TriggerEvent.ChargerChanged(true))
        release.complete(Unit)
        job.join()

        assertEquals(1, calls.size)
        assertEquals(0, second.size)
    }

    @Test
    fun aRuleIsNotRestartedInsideTheLoopWindow() = runBlocking {
        var now = 1_000L
        val recorder = Recorder()
        val engine = AutomationEngine(
            rules = { listOf(rule(actions = listOf(Action.Wait(1)))) },
            executor = recorder,
            now = { now },
        )

        engine.dispatch(TriggerEvent.ChargerChanged(true))
        now += 1_000
        engine.dispatch(TriggerEvent.ChargerChanged(true))
        assertEquals(1, recorder.actions.size)

        now += 1_100
        engine.dispatch(TriggerEvent.ChargerChanged(true))
        assertEquals(2, recorder.actions.size)
    }

    @Test
    fun aFailedActionDoesNotStopTheNextOne() = runBlocking {
        val engine = AutomationEngine(
            rules = { listOf(rule(actions = listOf(Action.Wait(1), Action.CircleToSearch))) },
            executor = ActionExecutor { action ->
                if (action is Action.Wait) ActionResult.Failed("không ghi được") else ActionResult.Done
            },
        )

        val steps = engine.dispatch(TriggerEvent.ChargerChanged(true)).single().steps

        assertEquals(ActionResult.Failed("không ghi được"), steps[0].result)
        assertEquals(ActionResult.Done, steps[1].result)
    }

    @Test
    fun aThrownActionBecomesAFailure() = runBlocking {
        val engine = AutomationEngine(
            rules = { listOf(rule()) },
            executor = ActionExecutor { error("boom") },
        )

        val result = engine.dispatch(TriggerEvent.ChargerChanged(true)).single().steps.single().result

        assertEquals(ActionResult.Failed("boom"), result)
    }

    @Test
    fun timeAndLocationEventsOnlyReachTheirRule() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine(
            {
                listOf(
                    rule(id = "morning", trigger = Trigger.TimeOfDay(7, 30)),
                    rule(id = "evening", trigger = Trigger.TimeOfDay(21, 0)),
                )
            },
            recorder,
        )

        engine.dispatch(TriggerEvent.TimeReached(7, 30, "morning"))
        assertEquals(1, recorder.actions.size)

        // A time event aimed at a rule the trigger does not belong to must not fall through to another rule.
        engine.dispatch(TriggerEvent.TimeReached(7, 30, "missing"))
        assertEquals(1, recorder.actions.size)
    }

    @Test
    fun aRuleMissingPermissionsIsSkippedEntirely() = runBlocking {
        val recorder = Recorder()
        val log = mutableListOf<AutomationLogEntry>()
        val engine = AutomationEngine(
            rules = { listOf(rule()) },
            executor = recorder,
            log = { log += it },
            canRun = { false },
        )

        engine.dispatch(TriggerEvent.ChargerChanged(true))

        assertEquals(0, recorder.actions.size)
        assertTrue(log.single().result is ActionResult.Skipped)
    }

    @Test
    fun anInvalidRuleNeverRuns() = runBlocking {
        val recorder = Recorder()
        val engine = AutomationEngine(
            rules = { listOf(rule(name = "", actions = listOf(Action.Wait(1)))) },
            executor = recorder,
        )

        engine.dispatch(TriggerEvent.ChargerChanged(true))

        assertEquals(0, recorder.actions.size)
    }

    @Test
    fun logLinesAreWrittenForEveryAction() = runBlocking {
        val log = mutableListOf<AutomationLogEntry>()
        val engine = AutomationEngine(
            rules = { listOf(rule(name = "Pin", actions = listOf(Action.Wait(1), Action.CircleToSearch))) },
            executor = Recorder(),
            log = { log += it },
        )

        engine.dispatch(TriggerEvent.ChargerChanged(true))

        assertEquals(2, log.size)
        assertEquals("Pin", log.first().ruleName)
    }
}
