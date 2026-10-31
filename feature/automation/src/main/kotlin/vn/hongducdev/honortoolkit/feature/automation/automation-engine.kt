package vn.hongducdev.honortoolkit.feature.automation

import kotlin.coroutines.cancellation.CancellationException

/** Runs one action. Implementations return [ActionResult] instead of throwing for expected failures. */
fun interface ActionExecutor {
    suspend fun run(action: Action): ActionResult
}

/**
 * Matches a [TriggerEvent] against the enabled rules and runs their actions one after another.
 *
 * A rule that is still running loses the new turn instead of queuing it, because a rule may contain
 * a [Action.Wait] and two overlapping copies would fight over the same settings. For the same reason
 * a rule does not start again within [loopWindowMs] of its previous start, which stops
 * "mute → notification → unmute" style loops.
 */
class AutomationEngine(
    private val rules: () -> List<AutomationRule>,
    private val executor: ActionExecutor,
    private val log: (AutomationLogEntry) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
    private val loopWindowMs: Long = 2_000L,
    /** False while a rule is missing a permission; it is skipped instead of half-running. */
    private val canRun: (AutomationRule) -> Boolean = { true },
) {
    private val stateLock = Any()
    private val running = mutableSetOf<String>()
    private val lastStarted = mutableMapOf<String, Long>()

    suspend fun dispatch(event: TriggerEvent): List<AutomationOutcome> {
        val outcomes = mutableListOf<AutomationOutcome>()
        for (rule in rules()) {
            if (!rule.enabled || !rule.isValid) continue
            if (!matches(rule, event)) continue
            if (!canRun(rule)) {
                log(
                    AutomationLogEntry(
                        at = now(),
                        ruleName = rule.name,
                        action = "Kích hoạt",
                        result = ActionResult.Skipped("chưa cấp đủ quyền cho quy tắc"),
                    ),
                )
                continue
            }
            if (!claim(rule.id)) {
                log(
                    AutomationLogEntry(
                        at = now(),
                        ruleName = rule.name,
                        action = "Kích hoạt",
                        result = ActionResult.Skipped("quy tắc đang chạy hoặc vừa chạy xong"),
                    ),
                )
                continue
            }
            val steps = try {
                rule.actions.map { action -> AutomationStep(action, execute(action)) }
            } finally {
                release(rule.id)
            }
            steps.forEach { step ->
                log(AutomationLogEntry(now(), rule.name, step.action.summary(), step.result))
            }
            outcomes += AutomationOutcome(rule.id, rule.name, steps)
        }
        return outcomes
    }

    /** Forgets the loop window and running state; used by the tests and when all rules are disabled. */
    fun reset() = synchronized(stateLock) {
        running.clear()
        lastStarted.clear()
    }

    private fun matches(rule: AutomationRule, event: TriggerEvent): Boolean {
        val targetRuleId = when (event) {
            is TriggerEvent.TimeReached -> event.ruleId
            is TriggerEvent.LocationChanged -> event.ruleId
            else -> null
        }
        if (targetRuleId != null && targetRuleId != rule.id) return false
        return rule.trigger.matches(event)
    }

    private fun claim(ruleId: String): Boolean = synchronized(stateLock) {
        val at = now()
        if (ruleId in running) return false
        val previous = lastStarted[ruleId]
        if (previous != null && at - previous < loopWindowMs) return false
        running += ruleId
        lastStarted[ruleId] = at
        true
    }

    private fun release(ruleId: String) {
        synchronized(stateLock) { running -= ruleId }
    }

    private suspend fun execute(action: Action): ActionResult = try {
        executor.run(action)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ActionResult.Failed(e.message ?: e::class.java.simpleName)
    }
}
