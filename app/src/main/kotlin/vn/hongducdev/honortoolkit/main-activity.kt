package vn.hongducdev.honortoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Text
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.rememberMotionEnabled
import vn.hongducdev.honortoolkit.feature.circlesearch.CircleSearchScreen
import vn.hongducdev.honortoolkit.feature.debloat.DebloatScreen
import vn.hongducdev.honortoolkit.ui.home.ConnectionScreen
import vn.hongducdev.honortoolkit.ui.home.HomeScreen
import vn.hongducdev.honortoolkit.ui.theme.HonorToolkitTheme


private enum class Screen(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    CircleSearch(R.string.nav_circle_search, Icons.Rounded.Search),
    Debloat(R.string.nav_debloat, Icons.Rounded.DeleteSweep),
    Connection(R.string.nav_connection, Icons.Rounded.Link),
}

private val ToolbarClearance = 88.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonorToolkitTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                val adbState by AdbSession.get(application).state.collectAsState()
                val connected = adbState == AdbState.Connected
                // Debloat needs ADB; fall back to Home if the connection drops while it is open.
                LaunchedEffect(connected, screen) { if (!connected && screen == Screen.Debloat) screen = Screen.Home }
                val motionEnabled = rememberMotionEnabled()
                if (screen != Screen.Home) BackHandler { screen = Screen.Home }
                val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
                val haptics = LocalHapticFeedback.current
                Box(modifier = Modifier.nestedScroll(toolbarScroll)) {
                AnimatedContent(
                    modifier = Modifier.padding(bottom = ToolbarClearance),
                    targetState = screen,
                    transitionSpec = {
                        // No blur here: blurring a whole screen while it is first composed drops frames.
                        if (motionEnabled) {
                            (fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) + scaleIn(initialScale = 0.94f)) togetherWith
                                (fadeOut(spring(stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 1.04f))
                        } else {
                            fadeIn() togetherWith fadeOut()
                        }
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.Debloat -> DebloatScreen()
                        Screen.CircleSearch -> CircleSearchScreen()
                        Screen.Connection -> ConnectionScreen()
                        Screen.Home -> HomeScreen(
                            onOpenDebloat = { screen = Screen.Debloat },
                            onOpenCircleSearch = { screen = Screen.CircleSearch },
                            onOpenConnection = { screen = Screen.Connection },
                        )
                    }
                }
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    scrollBehavior = toolbarScroll,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                ) {
                    Screen.entries.filter { it != Screen.Debloat || connected }.forEach { item ->
                        val label = stringResource(item.labelRes)
                        val select = {
                            if (item != screen) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            screen = item
                        }
                        if (item == screen) {
                            FilledTonalButton(onClick = select) {
                                Icon(item.icon, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(label)
                            }
                        } else {
                            IconButton(onClick = select) { Icon(item.icon, contentDescription = label) }
                        }
                    }
                }
                }
            }
        }
    }
}
