package vn.hongducdev.honortoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.hongducdev.honortoolkit.feature.quickshare.QuickShareSendScreen
import vn.hongducdev.honortoolkit.feature.quickshare.sharedStreamUris
import vn.hongducdev.honortoolkit.ui.theme.HonorToolkitTheme

/** Target of the system share sheet: sends the shared files through Quick Share. */
class QuickShareSendActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uris = sharedStreamUris(intent)
        setContent {
            HonorToolkitTheme {
                QuickShareSendScreen(uris = uris, onClose = ::finish)
            }
        }
    }
}
