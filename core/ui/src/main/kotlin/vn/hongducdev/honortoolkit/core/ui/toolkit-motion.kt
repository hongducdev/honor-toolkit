package vn.hongducdev.honortoolkit.core.ui

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Decorative motion is off in battery saver and when the user disabled animations system-wide. */
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) { isMotionEnabled(context) }
}

private fun isMotionEnabled(context: Context): Boolean {
    val powerSave = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    val animationScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return !powerSave && animationScale > 0f
}
