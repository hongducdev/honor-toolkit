package vn.hongducdev.honortoolkit.feature.circlesearch

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsNote
import vn.hongducdev.honortoolkit.core.ui.SliderRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

// The hub is a top-level destination (no back arrow); system back from a sub-page returns to the hub.
@Composable
fun CircleSearchScreen(modifier: Modifier = Modifier, viewModel: CircleSearchViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var page by rememberSaveable { mutableStateOf(CsPage.Hub) }

    BackHandler(enabled = page != CsPage.Hub) { page = CsPage.Hub }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    ProvideToolbar(
        if (page == CsPage.Hub) {
            ToolbarSpec.Tabs()
        } else {
            ToolbarSpec.Page(
                title = stringResource(page.titleRes),
                backDescription = stringResource(R.string.cs_back),
                onBack = { page = CsPage.Hub },
                fab = if (page == CsPage.Trigger) ToolbarAction(Icons.Rounded.PlayArrow, stringResource(R.string.cs_test_button), viewModel::test) else null,
            )
        },
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp).verticalScroll(rememberScrollState()).padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (page) {
                CsPage.Hub -> {
                    StatusCard(state, viewModel)
                    SettingsGroup(null) {
                        CsPage.entries.filter { it != CsPage.Hub }.forEach { target ->
                            NavRow(target.icon, stringResource(target.titleRes), stringResource(target.descriptionRes)) { page = target }
                        }
                    }
                }
                CsPage.Trigger -> {
                    SettingsGroup(stringResource(R.string.cs_trigger_heading)) {
                        SliderRow({ stringResource(R.string.cs_delay_label, it) }, state.delayMs, 0, TriggerConfig.MAX_DELAY_MS, STEP_MS, viewModel::setDelay)
                        SliderRow({ stringResource(R.string.cs_tile_delay_label, it) }, state.tileDelayMs, 0, TriggerConfig.MAX_DELAY_MS, STEP_MS, viewModel::setTileDelay)
                        SwitchRow(stringResource(R.string.cs_vibrate_label), state.vibrate, viewModel::setVibrate)
                    }
                    if (state.lastResult.isNotEmpty()) {
                        Text(
                            stringResource(R.string.cs_last_result, state.lastResult),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    SettingsGroup(stringResource(R.string.cs_assign_heading)) {
                        SettingsNote(stringResource(R.string.cs_assign_body))
                        SettingsNote(stringResource(R.string.cs_old_app_note))
                    }
                }
                CsPage.Launcher -> SettingsGroup(stringResource(R.string.cs_launcher_heading)) {
                    SettingsNote(stringResource(R.string.cs_launcher_body))
                    listOf(
                        LauncherEntry.CircleSearch to R.string.cs_launcher_circle_search,
                        LauncherEntry.Gemini to R.string.cs_launcher_gemini,
                        LauncherEntry.SearchLive to R.string.cs_launcher_live,
                    ).forEach { (entry, label) ->
                        SwitchRow(stringResource(label), entry in state.launcherEntries) { viewModel.setLauncherEntry(entry, it) }
                    }
                }
                CsPage.Strip -> SettingsGroup(stringResource(R.string.cs_strip_heading)) {
                    SettingsNote(stringResource(R.string.cs_strip_hint))
                    SettingsNote(
                        stringResource(
                            when {
                                !state.overlayGranted -> R.string.cs_strip_no_permission
                                state.stripEnabled -> R.string.cs_strip_on
                                else -> R.string.cs_strip_off
                            },
                        ),
                    )
                    SwitchRow(
                        stringResource(R.string.cs_strip_switch_label),
                        state.stripEnabled && state.overlayGranted,
                    ) { enabled ->
                        viewModel.setStripEnabled(enabled)
                        if (enabled && !state.overlayGranted) {
                            context.startActivity(
                                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")),
                            )
                        }
                    }
                    SliderRow({ stringResource(R.string.cs_strip_width_label, it) }, state.stripWidthPercent, 5, TriggerConfig.MAX_STRIP_WIDTH_PERCENT, 5, viewModel::setStripWidth)
                    SliderRow({ stringResource(R.string.cs_strip_height_label, it) }, state.stripHeightDp, 2, TriggerConfig.MAX_STRIP_HEIGHT_DP, 2, viewModel::setStripHeight)
                    SliderRow(
                        { stringResource(R.string.cs_strip_offset_label, it) },
                        state.stripOffsetDp,
                        TriggerConfig.MIN_STRIP_OFFSET_DP,
                        TriggerConfig.MAX_STRIP_OFFSET_DP,
                        2,
                        viewModel::setStripOffset,
                    )
                    SwitchRow(stringResource(R.string.cs_strip_visible_label), state.stripVisible, viewModel::setStripVisible)
                }
            }
        }
    }
}

private enum class CsPage(val titleRes: Int, val descriptionRes: Int, val icon: ImageVector) {
    Hub(R.string.cs_title, 0, Icons.Rounded.Apps),
    Trigger(R.string.cs_page_trigger, R.string.cs_page_trigger_desc, Icons.Rounded.TouchApp),
    Launcher(R.string.cs_page_launcher, R.string.cs_page_launcher_desc, Icons.Rounded.Widgets),
    Strip(R.string.cs_page_strip, R.string.cs_page_strip_desc, Icons.Rounded.SwipeUp),
}

@Composable
private fun StatusCard(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    val ready = state.readiness == Readiness.Ready
    val container = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    when (state.readiness) {
                        Readiness.NoGoogle -> R.string.cs_status_no_google
                        Readiness.NotAssistant -> R.string.cs_status_not_assistant
                        Readiness.Ready -> R.string.cs_status_ready
                    },
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (state.busy) ToolkitLoadingIndicator()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.readiness == Readiness.NotAssistant) {
                    Button(onClick = viewModel::setGoogleAsAssistant, enabled = !state.busy) {
                        Text(stringResource(R.string.cs_assistant_set))
                    }
                }
                if (state.canRestore) {
                    OutlinedButton(onClick = viewModel::restoreAssistant, enabled = !state.busy) {
                        Text(stringResource(R.string.cs_assistant_restore))
                    }
                }
            }
        }
    }
}

@Composable
private fun messageText(message: CircleSearchMessage): String = when (message) {
    CircleSearchMessage.AssistantSet -> stringResource(R.string.cs_assistant_done)
    CircleSearchMessage.AssistantRestored -> stringResource(R.string.cs_assistant_restored)
    CircleSearchMessage.NothingToRestore -> stringResource(R.string.cs_assistant_nothing_to_restore)
    CircleSearchMessage.NeedsAdb -> stringResource(R.string.cs_assistant_needs_adb)
    is CircleSearchMessage.AssistantFailed -> stringResource(R.string.cs_assistant_failed, message.reason)
}

private const val STEP_MS = 50L
