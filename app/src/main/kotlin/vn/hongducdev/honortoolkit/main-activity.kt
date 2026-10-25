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
import vn.hongducdev.honortoolkit.feature.circlesearch.CircleSearchScreen
import vn.hongducdev.honortoolkit.feature.debloat.DebloatScreen
import vn.hongducdev.honortoolkit.ui.home.HomeScreen
import vn.hongducdev.honortoolkit.ui.theme.HonorToolkitTheme

private val TransitionBlur = 18.dp

private enum class Screen { Home, Debloat, CircleSearch }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonorToolkitTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                val motionEnabled = rememberMotionEnabled()
                if (screen != Screen.Home) BackHandler { screen = Screen.Home }
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (motionEnabled) {
                            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.94f)) togetherWith
                                (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 1.04f))
                        } else {
                            fadeIn() togetherWith fadeOut()
                        }
                    },
                    label = "screen",
                ) { target ->
                    val blurRadius: Dp by transition.animateDp(label = "screen-blur") { state ->
                        if (motionEnabled && state != EnterExitState.Visible) TransitionBlur else 0.dp
                    }
                    val blurModifier = if (blurRadius > 0.dp) Modifier.blur(blurRadius) else Modifier
                    when (target) {
                        Screen.Debloat -> DebloatScreen(onBack = { screen = Screen.Home }, modifier = blurModifier)
                        Screen.CircleSearch -> CircleSearchScreen(onBack = { screen = Screen.Home }, modifier = blurModifier)
                        Screen.Home -> HomeScreen(
                            onOpenDebloat = { screen = Screen.Debloat },
                            onOpenCircleSearch = { screen = Screen.CircleSearch },
                            modifier = blurModifier,
                        )
                    }
                }
            }
        }
    }
}
