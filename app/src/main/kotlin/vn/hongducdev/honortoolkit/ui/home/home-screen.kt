package vn.hongducdev.honortoolkit.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.feature.update.UpdateAvailableCard
import vn.hongducdev.honortoolkit.feature.update.UpdateNavRow
import vn.hongducdev.honortoolkit.feature.update.UpdateViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenApps: () -> Unit,
    onOpenCircleSearch: () -> Unit,
    onOpenConnection: () -> Unit,
    onOpenDeviceInfo: () -> Unit,
    onOpenQuickShare: () -> Unit,
    onOpenUpdate: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    updateViewModel: UpdateViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()
    val pendingUpdate = updateState.visibleUpdate
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val pullFraction = pullState.distanceFraction
    val armed = pullFraction >= 1f

    LaunchedEffect(armed) { if (armed) haptics.performHapticFeedback(HapticFeedbackType.ContextClick) }
    LaunchedEffect(refreshing) {
        if (refreshing) {
            onOpenDeviceInfo()
            // Let the page start sliding in before the pull state resets behind it.
            delay(PullResetDelayMs)
            refreshing = false
        }
    }

    val openInfoLabel = stringResource(R.string.home_open_device_info_action)
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true },
            state = pullState,
            indicator = {},
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp)
                .semantics { customActions = listOf(CustomAccessibilityAction(openInfoLabel) { onOpenDeviceInfo(); true }) }
                .verticalScroll(rememberScrollState()).padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PullHintCard(fraction = if (refreshing) 1f else pullFraction, armed = armed || refreshing)
            if (pendingUpdate != null) UpdateAvailableCard(pendingUpdate.versionName, onOpenUpdate)
            ConnectionStatusCard(state = state.adb, busy = state.busy, onReconnect = viewModel::reconnect)
            SettingsGroup(stringResource(R.string.home_tools_heading)) {
                // Circle to Search works without ADB (only setting the assistant needs it), so it is always listed.
                NavRow(
                    Icons.Rounded.Search,
                    stringResource(R.string.home_open_circle_search),
                    stringResource(R.string.home_circle_search_description),
                    onOpenCircleSearch,
                )
                if (state.adb == AdbState.Connected) {
                    NavRow(
                        Icons.Rounded.Apps,
                        stringResource(R.string.home_open_apps),
                        stringResource(R.string.home_apps_description),
                        onOpenApps,
                    )
                }
                NavRow(
                    Icons.Rounded.Share,
                    stringResource(R.string.home_open_quick_share),
                    stringResource(R.string.home_quick_share_description),
                    onOpenQuickShare,
                )
                NavRow(
                    Icons.Rounded.Link,
                    stringResource(R.string.home_open_connection),
                    stringResource(R.string.home_connection_description),
                    onOpenConnection,
                )
                UpdateNavRow(updateState.currentVersion, pendingUpdate?.versionName, onOpenUpdate)
            }
        }
        }
    }
}

@Composable
private fun PullHintCard(fraction: Float, armed: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val height by animateDpAsState(
        targetValue = PullHintHeight * fraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "pullHintHeight",
    )
    val container by animateColorAsState(if (armed) scheme.primary else scheme.surfaceContainerHigh, label = "pullHintContainer")
    val content by animateColorAsState(if (armed) scheme.onPrimary else scheme.onSurfaceVariant, label = "pullHintContent")
    if (height <= 0.dp) return
    Box(
        modifier = Modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.extraLarge).background(container),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.wrapContentHeight(unbounded = true).graphicsLayer { alpha = fraction.coerceIn(0f, 1f) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = content)
            Text(
                stringResource(if (armed) R.string.home_pull_release else R.string.home_pull_hint),
                style = MaterialTheme.typography.titleMedium,
                color = content,
            )
        }
    }
}

private val PullHintHeight = 72.dp
private const val PullResetDelayMs = 300L
