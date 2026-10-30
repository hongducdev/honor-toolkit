package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

// Runs after the caller activity has finished so the screenshot Google takes is of the app underneath.
// A failed request is retried once after waking Google, because a frozen Google process refuses the
// first session (see GoogleWarmUp).
object TriggerRunner {
    private const val ACTION_CIRCLE = "circle"
    private const val ACTION_GEMINI = "gemini"
    private const val ACTION_ASSISTANT = "assistant"
    private const val RETRY_AFTER_WARM_UP_MS = 120L
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, delayMs: Long, source: TriggerSource) =
        schedule(context, delayMs) { launchCircle(it, source, retried = false) }

    fun runGemini(context: Context, delayMs: Long, source: TriggerSource) =
        schedule(context, delayMs) { launchGemini(it, source, retried = false) }

    fun runAssistant(context: Context, delayMs: Long, source: TriggerSource) =
        schedule(context, delayMs) { openAssistant(it, source) }

    private fun schedule(context: Context, delayMs: Long, block: (Context) -> Unit) {
        val app = context.applicationContext
        // The strip is an overlay: it would otherwise be part of the screenshot Circle to Search reads.
        main.postDelayed({ NavBarStripService.hideForCapture { block(app) } }, delayMs)
    }

    private fun launchCircle(app: Context, source: TriggerSource, retried: Boolean) {
        if (!retried) GoogleWarmUp.prewarm(app)
        when (val result = CircleSearchLauncher.launch()) {
            is LaunchResult.Started -> succeed(app, source, ACTION_CIRCLE, result.detail, retried)
            is LaunchResult.Failed ->
                if (!retried) {
                    GoogleWarmUp.warmUp(app)
                    main.postDelayed({ launchCircle(app, source, retried = true) }, RETRY_AFTER_WARM_UP_MS)
                } else {
                    fail(app, source, ACTION_CIRCLE, result.reason, R.string.toast_failed)
                }
        }
    }

    private fun launchGemini(app: Context, source: TriggerSource, retried: Boolean) {
        if (!retried) GoogleWarmUp.prewarm(app)
        when (val result = CircleSearchLauncher.launchGemini()) {
            is LaunchResult.Started -> succeed(app, source, ACTION_GEMINI, result.detail, retried)
            is LaunchResult.Failed ->
                if (!retried) {
                    GoogleWarmUp.warmUp(app)
                    main.postDelayed({ launchGemini(app, source, retried = true) }, RETRY_AFTER_WARM_UP_MS)
                } else if (openGeminiIntent(app)) {
                    // Gemini has a public entry point, so a refused session still lands in the app.
                    TriggerConfig(app).lastResult = app.getString(R.string.result_gemini_intent)
                    TriggerLog.add(app, source, ACTION_GEMINI, "intent fallback after ${result.reason}", true)
                    vibrate(app)
                } else {
                    fail(app, source, ACTION_GEMINI, result.reason, R.string.toast_gemini_failed)
                }
        }
    }

    private fun openAssistant(app: Context, source: TriggerSource) {
        val started = runCatching {
            app.startActivity(Intent(Intent.ACTION_ASSIST).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (started) {
            succeed(app, source, ACTION_ASSISTANT, "ACTION_ASSIST", retried = false)
        } else {
            fail(app, source, ACTION_ASSISTANT, "no activity for ACTION_ASSIST", R.string.toast_assistant_failed)
        }
    }

    private fun openGeminiIntent(app: Context): Boolean = runCatching {
        app.startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    private fun succeed(app: Context, source: TriggerSource, action: String, detail: String, retried: Boolean) {
        val config = TriggerConfig(app)
        config.lastResult = app.getString(R.string.result_ok)
        TriggerLog.add(app, source, action, if (retried) "$detail after warm-up" else detail, true)
        if (config.vibrate) vibrate(app)
    }

    private fun fail(app: Context, source: TriggerSource, action: String, reason: String, toast: Int) {
        val assistant = CircleSearchLauncher.activeAssistant(app)
        TriggerConfig(app).lastResult = app.getString(R.string.result_failed, reason)
        TriggerLog.add(app, source, action, "$reason; assistant=$assistant", false)
        Toast.makeText(app, toast, Toast.LENGTH_SHORT).show()
    }

    private fun vibrate(app: Context) {
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }
}
