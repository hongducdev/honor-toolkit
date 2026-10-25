package vn.hongducdev.honortoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.FloatingToolbarExitDirection
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarHost
import vn.hongducdev.honortoolkit.core.ui.ToolbarHost
import vn.hongducdev.honortoolkit.core.ui.rememberMotionEnabled
import vn.hongducdev.honortoolkit.ui.navigation.bottomFade
import vn.hongducdev.honortoolkit.feature.circlesearch.CircleSearchScreen
import vn.hongducdev.honortoolkit.feature.debloat.DebloatScreen
import vn.hongducdev.honortoolkit.feature.notificationfix.NotificationFixScreen
import vn.hongducdev.honortoolkit.feature.permissions.PermissionsScreen
import vn.hongducdev.honortoolkit.ui.apps.AppsScreen
import vn.hongducdev.honortoolkit.ui.home.ConnectionScreen
import vn.hongducdev.honortoolkit.ui.home.HomeScreen
import vn.hongducdev.honortoolkit.ui.navigation.ToolbarDestination
import vn.hongducdev.honortoolkit.ui.navigation.ToolkitFloatingToolbar
import vn.hongducdev.honortoolkit.ui.theme.HonorToolkitTheme


private enum class Screen(val labelRes: Int, val icon: ImageVector, val inToolbar: Boolean = true) {
    Home(R.string.nav_home, Icons.Rounded.Home),
    CircleSearch(R.string.nav_circle_search, Icons.Rounded.Search),
    Apps(R.string.nav_apps, Icons.Rounded.Apps),
    Connection(R.string.nav_connection, Icons.Rounded.Link, inToolbar = false),
    // Sub-pages reached from Home or Apps stay off the toolbar, which holds at most four tabs.
    NotificationFix(R.string.nav_notification_fix, Icons.Rounded.Notifications, inToolbar = false),
    Debloat(R.string.nav_debloat, Icons.Rounded.Apps, inToolbar = false),
    Permissions(R.string.nav_permissions, Icons.Rounded.Apps, inToolbar = false),
    ;

    val needsAdb: Boolean get() = this == Debloat || this == Permissions
}

private val ToolbarHeight = 64.dp
private val ToolbarBottomMargin = 12.dp
private val ToolbarContentGap = 16.dp
private val ToolbarFadeExtra = 24.dp
private const val ToolbarFadeAlpha = 0.9f
private const val ScreenFadeInMillis = 200
private const val ScreenFadeOutMillis = 90

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
                // Debloat and Permissions need ADB; fall back to Apps if the connection drops while one is open.
                LaunchedEffect(connected, screen) { if (!connected && screen.needsAdb) screen = Screen.Apps }
                val motionEnabled = rememberMotionEnabled()
                when (screen) {
                    Screen.NotificationFix, Screen.Debloat, Screen.Permissions -> BackHandler { screen = Screen.Apps }
                    Screen.Home -> {}
                    else -> BackHandler { screen = Screen.Home }
                }
                val toolbarHost = remember { ToolbarHost() }
                val toolbarScroll = FloatingToolbarDefaults.exitAlwaysScrollBehavior(exitDirection = FloatingToolbarExitDirection.Bottom)
                val destinations = remember(connected) {
                    Screen.entries
                        .filter { it.inToolbar && (!it.needsAdb || connected) }
                        .map { ToolbarDestination(it, it.labelRes, it.icon) }
                }
                // Screens scroll behind the toolbar; each leaves this much room at its end (Scaffold already pads the navigation bar).
                val toolbarClearance = ToolbarHeight + ToolbarBottomMargin + ToolbarContentGap
                val fadeHeight = toolbarClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + ToolbarFadeExtra
                val fadeOverlay = MaterialTheme.colorScheme.background.copy(alpha = ToolbarFadeAlpha)
                Box(modifier = Modifier.nestedScroll(toolbarScroll)) {
                CompositionLocalProvider(LocalToolbarClearance provides toolbarClearance, LocalToolbarHost provides toolbarHost) {
                AnimatedContent(
                    modifier = Modifier.bottomFade(height = fadeHeight, overlay = fadeOverlay),
                    targetState = screen,
                    transitionSpec = {
                        // Cross-fade only: both screens stay composed during the transition, so scaling them costs frames.
                        if (motionEnabled) {
                            fadeIn(tween(ScreenFadeInMillis, delayMillis = ScreenFadeOutMillis)) togetherWith fadeOut(tween(ScreenFadeOutMillis))
                        } else {
                            fadeIn(snap()) togetherWith fadeOut(snap())
                        }
                    },
                    label = "screen",
                ) { target ->
                    when (target) {
                        Screen.Debloat -> DebloatScreen(onBack = { screen = Screen.Apps })
                        Screen.Permissions -> PermissionsScreen(onBack = { screen = Screen.Apps })
                        Screen.Apps -> AppsScreen(
                            adbConnected = connected,
                            onOpenNotificationFix = { screen = Screen.NotificationFix },
                            onOpenDebloat = { screen = Screen.Debloat },
                            onOpenPermissions = { screen = Screen.Permissions },
                        )
                        Screen.CircleSearch -> CircleSearchScreen()
                        Screen.NotificationFix -> NotificationFixScreen(onBack = { screen = Screen.Apps })
                        Screen.Connection -> ConnectionScreen(onBack = { screen = Screen.Home })
                        Screen.Home -> HomeScreen(
                            onOpenApps = { screen = Screen.Apps },
                            onOpenCircleSearch = { screen = Screen.CircleSearch },
                            onOpenConnection = { screen = Screen.Connection },
                        )
                    }
                }
                }
                ToolkitFloatingToolbar(
                    destinations = destinations,
                    selectedId = screen,
                    onSelect = { screen = it },
                    spec = toolbarHost.active,
                    scrollBehavior = toolbarScroll,
                    motionEnabled = motionEnabled,
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = ToolbarBottomMargin),
                )
                }
            }
        }
    }
}
