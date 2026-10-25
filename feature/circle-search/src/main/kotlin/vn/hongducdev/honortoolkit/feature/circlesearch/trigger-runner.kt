package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast

// Runs after the caller activity has finished so the screenshot Google takes is of the app underneath.
object TriggerRunner {
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, delayMs: Long) {
        val app = context.applicationContext
        main.postDelayed({ launchNow(app) }, delayMs)
    }

    private fun launchNow(app: Context) {
        val config = TriggerConfig(app)
        when (val result = CircleSearchLauncher.launch()) {
            LaunchResult.Started -> {
                config.lastResult = app.getString(R.string.result_ok)
                if (config.vibrate) vibrate(app)
            }
            is LaunchResult.Failed -> {
                config.lastResult = app.getString(R.string.result_failed, result.reason)
                Toast.makeText(app, R.string.toast_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun vibrate(app: Context) {
        val vibrator = app.getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }
}
