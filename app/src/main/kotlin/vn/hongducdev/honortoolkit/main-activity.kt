package vn.hongducdev.honortoolkit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.ui.navigation.SubPageEnterSpec
import vn.hongducdev.honortoolkit.ui.navigation.SubPageExitSpec
import vn.hongducdev.honortoolkit.ui.navigation.SubPageHost
import vn.hongducdev.honortoolkit.ui.navigation.SubPageSettleSpec
import vn.hongducdev.honortoolkit.ui.navigation.subPageUnderlay
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.graphics.vector.ImageVector
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

    val needsAdb: Boolean get() = this == Apps || this == NotificationFix || this == Debloat || this == Permissions
}

private val ToolbarHeight = 64.dp
private val ToolbarBottomMargin = 12.dp
private val ToolbarContentGap = 16.dp
private val ToolbarFadeExtra = 24.dp
private const val ToolbarFadeAlpha = 0.9f

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HonorToolkitTheme {
                val adbState by AdbSession.get(application).state.collectAsState()
                val connected = adbState == AdbState.Connected
                val tabs = remember(connected) { Screen.entries.filter { it.inToolbar && (!it.needsAdb || connected) } }
                val pagerState = rememberPagerState(pageCount = { tabs.size })
                var subPage by rememberSaveable { mutableStateOf<Screen?>(null) }
                val scope = rememberCoroutineScope()
                val haptics = LocalHapticFeedback.current
                val motionEnabled = rememberMotionEnabled()

                // Apps and its sub-pages need ADB; leave them if the connection drops while one is open.
                LaunchedEffect(connected) { if (!connected) subPage = null }
                LaunchedEffect(tabs.size) { if (pagerState.currentPage >= tabs.size) pagerState.scrollToPage(0) }

                val dragged by pagerState.interactionSource.collectIsDraggedAsState()
                val currentlyDragged by rememberUpdatedState(dragged)
                LaunchedEffect(pagerState) {
                    snapshotFlow { pagerState.currentPage }.drop(1).collect {
                        if (currentlyDragged) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }

                fun showTab(tab: Screen) {
                    val index = tabs.indexOf(tab)
                    if (index >= 0) scope.launch { if (motionEnabled) pagerState.animateScrollToPage(index) else pagerState.scrollToPage(index) }
                }

                BackHandler(enabled = subPage == null && pagerState.currentPage != 0) { showTab(Screen.Home) }
                val backProgress = remember { Animatable(0f) }
                // 0 = closed, 1 = open. The page stays composed until the close animation ends, so it can slide out.
                val reveal = remember { Animatable(if (subPage != null) 1f else 0f) }
                var shownPage by remember { mutableStateOf(subPage) }
                LaunchedEffect(subPage) {
                    if (subPage != null) {
                        // The page being opened must not inherit the shrink left over from the last back gesture.
                        backProgress.snapTo(0f)
                        shownPage = subPage
                        if (motionEnabled) reveal.animateTo(1f, SubPageEnterSpec) else reveal.snapTo(1f)
                    } else {
                        if (motionEnabled) reveal.animateTo(0f, SubPageExitSpec) else reveal.snapTo(0f)
                        shownPage = null
                    }
                }
                PredictiveBackHandler(enabled = subPage != null) { progress ->
                    try {
                        progress.collect { backProgress.snapTo(it.progress) }
                        subPage = null
                    } catch (e: CancellationException) {
                        backProgress.animateTo(0f, SubPageSettleSpec)
                        throw e
                    }
                }

                val toolbarHost = remember { ToolbarHost() }
                val destinations = remember(tabs) { tabs.map { ToolbarDestination(it, it.labelRes, it.icon) } }
                // Screens scroll behind the toolbar; each leaves this much room at its end (Scaffold already pads the navigation bar).
                val toolbarClearance = ToolbarHeight + ToolbarBottomMargin + ToolbarContentGap
                val fadeHeight = toolbarClearance + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + ToolbarFadeExtra
                val fadeOverlay = MaterialTheme.colorScheme.background.copy(alpha = ToolbarFadeAlpha)
                Box {
                    CompositionLocalProvider(LocalToolbarClearance provides toolbarClearance, LocalToolbarHost provides toolbarHost) {
                        Box(modifier = Modifier.bottomFade(height = fadeHeight, overlay = fadeOverlay)) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxSize().subPageUnderlay { reveal.value },
                                userScrollEnabled = subPage == null,
                                key = { tabs[it] },
                            ) { index ->
                                when (tabs[index]) {
                                    Screen.Home -> HomeScreen(
                                        onOpenApps = { showTab(Screen.Apps) },
                                        onOpenCircleSearch = { showTab(Screen.CircleSearch) },
                                        onOpenConnection = { subPage = Screen.Connection },
                                    )
                                    Screen.CircleSearch -> CircleSearchScreen()
                                    Screen.Apps -> AppsScreen(
                                        onOpenNotificationFix = { subPage = Screen.NotificationFix },
                                        onOpenDebloat = { subPage = Screen.Debloat },
                                        onOpenPermissions = { subPage = Screen.Permissions },
                                    )
                                    else -> Unit
                                }
                            }
                            SubPageHost(page = shownPage, reveal = { reveal.value }, backProgress = { backProgress.value }) { target ->
                                val close = { subPage = null }
                                when (target) {
                                    Screen.Connection -> ConnectionScreen(onBack = close)
                                    Screen.NotificationFix -> NotificationFixScreen(onBack = close)
                                    Screen.Debloat -> DebloatScreen(onBack = close)
                                    Screen.Permissions -> PermissionsScreen(onBack = close)
                                    else -> Unit
                                }
                            }
                        }
                    }
                    ToolkitFloatingToolbar(
                        destinations = destinations,
                        selectedId = tabs.getOrElse(pagerState.currentPage) { Screen.Home },
                        onSelect = ::showTab,
                        spec = toolbarHost.active,
                        motionEnabled = motionEnabled,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = ToolbarBottomMargin),
                    )
                }
            }
        }
    }
}
