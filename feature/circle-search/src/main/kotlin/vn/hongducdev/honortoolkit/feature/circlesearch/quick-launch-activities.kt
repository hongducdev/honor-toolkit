package vn.hongducdev.honortoolkit.feature.circlesearch

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/** Starts one fixed public intent and finishes at once, so a key or gesture lands in the target app directly. */
abstract class QuickLaunchActivity : Activity() {
    protected abstract fun target(): Intent

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val started = runCatching { startActivity(target().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!started) Toast.makeText(this, R.string.toast_quick_launch_failed, Toast.LENGTH_SHORT).show()
        finish()
    }
}

/** Opens the default assistant (Gemini on a Google setup) the way the assist button does. */
class GeminiActivity : QuickLaunchActivity() {
    override fun target() = Intent(Intent.ACTION_VOICE_COMMAND)
}

/** Opens the Google app's Search Live screen through its exported deep link. */
class SearchLiveActivity : QuickLaunchActivity() {
    override fun target() = Intent(Intent.ACTION_VIEW, Uri.parse(SEARCH_LIVE_URL)).setPackage(AssistantSetup.GOOGLE_APP)

    private companion object {
        const val SEARCH_LIVE_URL = "https://www.google.com/searchliveapp"
    }
}
