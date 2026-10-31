package vn.hongducdev.honortoolkit.feature.tune

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

/**
 * MagicOS tuning page. Every row writes a fixed set of settings and reads them back, so the
 * switches show what the phone actually kept rather than what the app asked for.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TuneScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: TuneViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val message = state.message?.let { messageText(it) }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.tune_title),
            backDescription = stringResource(R.string.tune_back),
            onBack = onBack,
            actions = listOf(
                ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.tune_refresh), viewModel::refresh),
                ToolbarAction(Icons.Rounded.RestartAlt, stringResource(R.string.tune_restore), viewModel::requestRestore),
            ),
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = LocalToolbarClearance.current)) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "intro") {
                Text(
                    stringResource(R.string.tune_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item(key = "recommended") {
                FilledTonalButton(
                    onClick = viewModel::requestRecommended,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.tune_recommended))
                }
            }
            item(key = "recommended-desc") {
                Text(
                    stringResource(R.string.tune_recommended_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TuneGroup.entries.forEach { group ->
                item(key = "group-${group.name}") {
                    TuneGroupBlock(
                        group = group,
                        state = state,
                        enabled = true,
                        onToggle = viewModel::setToggle,
                        onChoose = viewModel::choose,
                        onResetGps = viewModel::resetGps,
                    )
                }
            }
        }
        if (state.loading) {
            ToolkitLoadingIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
                    .width(64.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            )
        }
        }
    }

    if (state.confirmRecommended) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRecommended,
            title = { Text(stringResource(R.string.tune_recommended_title)) },
            text = {
                Text(
                    stringResource(R.string.tune_recommended_body, TuneCatalog.recommended.size),
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { Button(onClick = viewModel::applyRecommended) { Text(stringResource(R.string.tune_recommended_ok)) } },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelRecommended) { Text(stringResource(R.string.tune_cancel)) } },
        )
    }

    if (state.confirmRestore) {
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text(stringResource(R.string.tune_restore_title)) },
            text = { Text(stringResource(R.string.tune_restore_body)) },
            confirmButton = { Button(onClick = viewModel::confirmRestore) { Text(stringResource(R.string.tune_restore_ok)) } },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelRestore) { Text(stringResource(R.string.tune_cancel)) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TuneGroupBlock(
    group: TuneGroup,
    state: TuneUiState,
    enabled: Boolean,
    onToggle: (TuneId, Boolean) -> Unit,
    onChoose: (TuneId, String) -> Unit,
    onResetGps: () -> Unit,
) {
    SettingsGroup(title = stringResource(groupTitle(group))) {
        TuneCatalog.choices.filter { it.group == group }.forEach { spec ->
            ChoiceRow(
                spec = spec,
                selected = spec.selectionOf(state.choices[spec.id]),
                enabled = enabled,
                onSelect = { onChoose(spec.id, it) },
            )
        }
        TuneCatalog.toggles.filter { it.group == group }.forEach { spec ->
            ToggleRow(spec = spec, state = state, enabled = enabled, onChange = { onToggle(spec.id, it) })
        }
        if (group == TuneGroup.Network) {
            SettingsRow {
                Text(stringResource(R.string.tune_gps_reset), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(R.string.tune_gps_reset_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onResetGps, enabled = enabled) {
                    Text(stringResource(R.string.tune_gps_reset_action))
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(spec: ToggleSpec, state: TuneUiState, enabled: Boolean, onChange: (Boolean) -> Unit) {
    val description = stringResource(toggleDescription(spec.id))
    val unknown = state.toggles.containsKey(spec.id) && state.toggles[spec.id] == null
    val unknownNote = if (unknown) stringResource(R.string.tune_unknown) else null
    SwitchRow(
        label = stringResource(toggleTitle(spec.id)),
        checked = state.isOn(spec.id),
        onChange = onChange,
        description = if (unknownNote != null) "$description\n$unknownNote" else description,
        enabled = enabled,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(spec: ChoiceSpec, selected: String?, enabled: Boolean, onSelect: (String) -> Unit) {
    SettingsRow {
        Text(stringResource(choiceTitle(spec.id)), style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            spec.options.forEach { option ->
                FilterChip(
                    selected = option.value == selected,
                    onClick = { onSelect(option.value) },
                    enabled = enabled,
                    label = { Text(option.label) },
                )
            }
        }
        Text(
            stringResource(choiceDescription(spec.id)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun messageText(message: TuneMessage): String = when (message) {
    is TuneMessage.Applied -> stringResource(
        if (message.on) R.string.tune_msg_applied_on else R.string.tune_msg_applied_off,
    )
    TuneMessage.Refused -> stringResource(R.string.tune_msg_refused)
    TuneMessage.ShellFailed -> stringResource(R.string.tune_msg_shell_failed)
    TuneMessage.Unsupported -> stringResource(R.string.tune_msg_unsupported)
    is TuneMessage.Preset -> stringResource(R.string.tune_msg_preset, message.applied, message.total, message.skipped)
    is TuneMessage.Restored -> stringResource(R.string.tune_msg_restored, message.restored, message.total)
    TuneMessage.GpsSent -> stringResource(R.string.tune_msg_gps_sent)
    TuneMessage.GpsFailed -> stringResource(R.string.tune_msg_gps_failed)
}

private fun groupTitle(group: TuneGroup): Int = when (group) {
    TuneGroup.Performance -> R.string.tune_group_performance
    TuneGroup.Notifications -> R.string.tune_group_notifications
    TuneGroup.ChinaApps -> R.string.tune_group_china
    TuneGroup.Network -> R.string.tune_group_network
}

private fun choiceTitle(id: TuneId): Int = when (id) {
    TuneId.RefreshRate -> R.string.tune_refresh_rate
    TuneId.AnimationScale -> R.string.tune_animation
    else -> R.string.tune_title
}

private fun choiceDescription(id: TuneId): Int = when (id) {
    TuneId.RefreshRate -> R.string.tune_refresh_rate_desc
    TuneId.AnimationScale -> R.string.tune_animation_desc
    else -> R.string.tune_intro
}

private fun toggleDescription(id: TuneId): Int = when (id) {
    TuneId.TouchBoost -> R.string.tune_touch_boost_desc
    TuneId.GentleNotifications -> R.string.tune_gentle_desc
    TuneId.NotificationCooldown -> R.string.tune_cooldown_desc
    TuneId.HeadsUp -> R.string.tune_heads_up_desc
    TuneId.BannerContent -> R.string.tune_banner_content_desc
    TuneId.LockScreenDetail -> R.string.tune_lock_screen_desc
    TuneId.JobBatching -> R.string.tune_batching_desc
    TuneId.HiBoard -> R.string.tune_hiboard_desc
    TuneId.HonorSearch -> R.string.tune_honor_search_desc
    TuneId.MagazineDownload -> R.string.tune_magazine_desc
    TuneId.YoyoNavWake -> R.string.tune_yoyo_desc
    TuneId.MarketAutoUpdate -> R.string.tune_market_desc
    TuneId.AdPersonalization -> R.string.tune_ads_desc
    TuneId.CaptivePortal -> R.string.tune_captive_desc
    TuneId.CallerIdMatch -> R.string.tune_caller_desc
    TuneId.Volte5g -> R.string.tune_volte_desc
    TuneId.SuplGps -> R.string.tune_supl_desc
    TuneId.PrivateDnsAdBlock -> R.string.tune_dns_desc
    else -> R.string.tune_intro
}
