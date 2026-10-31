package vn.hongducdev.honortoolkit.feature.automation

import android.content.Context
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide engine holder. The foreground service keeps the process and its dynamic receivers
 * alive, but accessibility, notification and alarm components can dispatch without waiting for the
 * service to be constructed first: the first caller builds the engine.
 */
object AutomationRuntime {
    private const val DONE_DEADLINE_MS = 8_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    @Volatile private var engine: AutomationEngine? = null

    fun engine(context: Context): AutomationEngine {
        engine?.let { return it }
        return synchronized(this) {
            engine ?: run {
                val store = AutomationStorage.store(context)
                AutomationEngine(
                    rules = store::rules,
                    executor = AndroidActionExecutor(context.applicationContext, store),
                    log = store::appendLog,
                    canRun = { AutomationPermissions.missing(context.applicationContext, it).isEmpty() },
                ).also { engine = it }
            }
        }
    }

    /**
     * Runs the matching rules off the caller's thread; a throw inside an action is already a Failed.
     * [onDone] runs once, when the rules finish or after [DONE_DEADLINE_MS], whichever is first: a
     * broadcast held with goAsync() is killed after about ten seconds, and a rule may Wait far longer.
     * The foreground service keeps the process alive, so the rule itself keeps running.
     */
    fun dispatch(context: Context, event: TriggerEvent, onDone: (() -> Unit)? = null) {
        val app = context.applicationContext
        val once = AtomicBoolean(false)
        val finish = { if (onDone != null && once.compareAndSet(false, true)) onDone() }
        if (onDone != null) {
            scope.launch {
                delay(DONE_DEADLINE_MS)
                finish()
            }
        }
        scope.launch {
            try {
                engine(app).dispatch(event)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AutomationStorage.store(app).appendLog(
                    AutomationLogEntry(
                        at = System.currentTimeMillis(),
                        ruleName = "Hệ thống",
                        action = "Nhận sự kiện",
                        result = ActionResult.Failed(e.message ?: e::class.java.simpleName),
                    ),
                )
            } finally {
                finish()
            }
        }
    }

    fun hasEnabledRule(context: Context, predicate: (Trigger) -> Boolean): Boolean =
        AutomationStorage.store(context).rules().any { it.enabled && it.isValid && predicate(it.trigger) }
}
