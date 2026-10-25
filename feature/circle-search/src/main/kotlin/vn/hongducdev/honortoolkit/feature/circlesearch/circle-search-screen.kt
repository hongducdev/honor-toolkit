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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.WavyProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleSearchScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: CircleSearchViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val backLabel = stringResource(R.string.cs_back)

    BackHandler(onBack = onBack)

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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics {
                            contentDescription = backLabel
                        })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state, viewModel)
            SectionCard(stringResource(R.string.cs_trigger_heading)) {
                LabeledSlider(R.string.cs_delay_label, state.delayMs, 0, TriggerConfig.MAX_DELAY_MS, STEP_MS, viewModel::setDelay)
                LabeledSlider(R.string.cs_tile_delay_label, state.tileDelayMs, 0, TriggerConfig.MAX_DELAY_MS, STEP_MS, viewModel::setTileDelay)
                SwitchRow(stringResource(R.string.cs_vibrate_label), state.vibrate, viewModel::setVibrate)
                FilledTonalButton(onClick = viewModel::test) { Text(stringResource(R.string.cs_test_button)) }
                if (state.lastResult.isNotEmpty()) {
                    Text(
                        stringResource(R.string.cs_last_result, state.lastResult),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SectionCard(stringResource(R.string.cs_assign_heading)) {
                Text(stringResource(R.string.cs_assign_body), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.cs_old_app_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionCard(stringResource(R.string.cs_launcher_heading)) {
                Text(stringResource(R.string.cs_launcher_body), style = MaterialTheme.typography.bodyMedium)
                listOf(
                    LauncherEntry.CircleSearch to R.string.cs_launcher_circle_search,
                    LauncherEntry.Gemini to R.string.cs_launcher_gemini,
                    LauncherEntry.SearchLive to R.string.cs_launcher_live,
                ).forEach { (entry, label) ->
                    SwitchRow(stringResource(label), entry in state.launcherEntries) { viewModel.setLauncherEntry(entry, it) }
                }
            }
            SectionCard(stringResource(R.string.cs_strip_heading)) {
                Text(
                    stringResource(R.string.cs_strip_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(
                        when {
                            !state.overlayGranted -> R.string.cs_strip_no_permission
                            state.stripEnabled -> R.string.cs_strip_on
                            else -> R.string.cs_strip_off
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
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
                LabeledSlider(R.string.cs_strip_width_label, state.stripWidthPercent, 5, TriggerConfig.MAX_STRIP_WIDTH_PERCENT, 5, viewModel::setStripWidth)
                LabeledSlider(R.string.cs_strip_height_label, state.stripHeightDp, 2, TriggerConfig.MAX_STRIP_HEIGHT_DP, 2, viewModel::setStripHeight)
                LabeledSlider(
                    R.string.cs_strip_offset_label,
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

@Composable
private fun StatusCard(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    val ready = state.readiness == Readiness.Ready
    val container = if (ready) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
    val content = if (ready) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
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
            if (state.busy) WavyProgressIndicator()
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
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// labelRes takes one %d placeholder for the current value. The slider keeps its own position while
// dragging and writes the setting only when the drag ends.
@Composable
private fun LabeledSlider(labelRes: Int, value: Long, min: Long, max: Long, step: Long, onCommit: (Long) -> Unit) {
    var dragging by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val snapped = (min + ((dragging.toLong() - min) / step) * step).coerceIn(min, max)
    Column {
        Text(stringResource(labelRes, snapped), style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = snapped.toFloat(),
            onValueChange = { dragging = it },
            onValueChangeFinished = { onCommit(snapped) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = ((max - min) / step - 1).toInt().coerceAtLeast(0),
        )
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
