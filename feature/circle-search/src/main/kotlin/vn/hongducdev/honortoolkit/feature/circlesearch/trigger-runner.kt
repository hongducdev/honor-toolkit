package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

// Runs after the caller activity has finished so the screenshot Google takes is of the app underneath.
object TriggerRunner {
    private const val ACTION_CIRCLE = "circle"
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, delayMs: Long, source: TriggerSource) {
        val app = context.applicationContext
        main.postDelayed({ hideThenLaunch(app, source) }, delayMs)
    }

    private fun hideThenLaunch(app: Context, source: TriggerSource) {
        // The strip is an overlay: it would otherwise be part of the screenshot Circle to Search reads.
        NavBarStripService.hideForCapture { launchNow(app, source) }
    }

    private fun launchNow(app: Context, source: TriggerSource) {
        val config = TriggerConfig(app)
        when (val result = CircleSearchLauncher.launch()) {
            is LaunchResult.Started -> {
                config.lastResult = app.getString(R.string.result_ok)
                TriggerLog.add(app, source, ACTION_CIRCLE, result.detail, true)
                if (config.vibrate) vibrate(app)
            }
            is LaunchResult.Failed -> {
                val assistant = CircleSearchLauncher.activeAssistant(app)
                config.lastResult = app.getString(R.string.result_failed, result.reason)
                TriggerLog.add(app, source, ACTION_CIRCLE, "${result.reason}; assistant=$assistant", false)
                Toast.makeText(app, R.string.toast_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun vibrate(app: Context) {
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }
}
