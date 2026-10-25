package vn.hongducdev.honortoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.rememberMotionEnabled
import vn.hongducdev.honortoolkit.feature.debloat.DebloatScreen
import vn.hongducdev.honortoolkit.ui.home.HomeScreen
import vn.hongducdev.honortoolkit.ui.theme.HonorToolkitTheme

private val TransitionBlur = 18.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonorToolkitTheme {
                var showDebloat by rememberSaveable { mutableStateOf(false) }
                val motionEnabled = rememberMotionEnabled()
                if (showDebloat) BackHandler { showDebloat = false }
                AnimatedContent(
                    targetState = showDebloat,
                    transitionSpec = {
                        if (motionEnabled) {
                            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.94f)) togetherWith
                                (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 1.04f))
                        } else {
                            fadeIn() togetherWith fadeOut()
                        }
                    },
                    label = "screen",
                ) { debloat ->
                    val blurRadius: Dp by transition.animateDp(label = "screen-blur") { state ->
                        if (motionEnabled && state != EnterExitState.Visible) TransitionBlur else 0.dp
                    }
                    val blurModifier = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier
                    if (debloat) {
                        DebloatScreen(onBack = { showDebloat = false }, modifier = blurModifier)
                    } else {
                        HomeScreen(onOpenDebloat = { showDebloat = true }, modifier = blurModifier)
                    }
                }
            }
        }
    }
}
