package vn.hongducdev.honortoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.hongducdev.honortoolkit.ui.home.HomeScreen
import vn.hongducdev.honortoolkit.ui.theme.HonorToolkitTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonorToolkitTheme {
                HomeScreen()
            }
        }
    }
}
