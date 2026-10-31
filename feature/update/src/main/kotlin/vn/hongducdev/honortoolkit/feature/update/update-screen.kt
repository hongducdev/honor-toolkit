package vn.hongducdev.honortoolkit.feature.update

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlin.math.roundToInt
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: UpdateViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // The system installer permission is granted on another screen; refresh it when coming back.
    LifecycleResumeEffect(Unit) {
        viewModel.onResumed()
        onPauseOrDispose { }
    }

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.update_title),
            backDescription = stringResource(R.string.update_back),
            onBack = onBack,
            fab = ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.update_refresh), viewModel::check),
        ),
    )

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VersionCard(state)
            state.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            if (state.checking) {
                SettingsGroup(null) {
                    SettingsRow { ToolkitLoadingIndicator() }
                }
            }

            val update = state.update
            when {
                update != null && update.versionName != state.skippedVersion -> NewVersionSection(state, update, viewModel)
                update != null -> SkippedSection(state, update, viewModel)
            }

            SettingsGroup(title = null) {
                SwitchRow(stringResource(R.string.update_auto_check), state.autoCheck, onChange = viewModel::setAutoCheck)
            }
        }
    }
}

@Composable
private fun VersionCard(state: UpdateUiState) {
    SettingsGroup(title = null) {
        SettingsRow {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    stringResource(R.string.update_current_version, state.currentVersion),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            Text(statusText(state), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun statusText(state: UpdateUiState): String {
    val visible = state.visibleUpdate
    return when {
        state.checking -> stringResource(R.string.update_checking)
        visible != null -> stringResource(R.string.update_nav_available, visible.versionName)
        state.update != null -> stringResource(R.string.update_skipped, state.update.versionName)
        state.upToDate -> stringResource(R.string.update_latest)
        else -> stringResource(R.string.update_never_checked)
    }
}

@Composable
private fun NewVersionSection(state: UpdateUiState, update: AvailableUpdate, viewModel: UpdateViewModel) {
    val context = LocalContext.current
    SettingsGroup(title = stringResource(R.string.update_available_heading, update.versionName)) {
        SettingsRow {
            Text(stringResource(R.string.update_release_notes_heading), style = MaterialTheme.typography.titleSmall)
            Text(
                releaseNotesToPlainText(update.notes).ifBlank { stringResource(R.string.update_notes_empty) }.take(MaxNotesChars),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(assetLine(update, context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (update.pageUrl.isNotBlank()) {
            NavRow(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.update_open_release), stringResource(R.string.update_open_release_desc), viewModel::openReleasePage)
        }
    }

    if (state.needsInstallPermission) {
        SettingsGroup(title = null) {
            SettingsRow {
                Text(
                    stringResource(R.string.update_permission_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.downloading) {
            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            Text(
                stringResource(R.string.update_downloading, (state.progress * 100).roundToInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = viewModel::cancelDownload) { Text(stringResource(R.string.update_cancel_download)) }
        } else {
            Button(onClick = { if (state.apkPath != null) viewModel.install() else viewModel.download() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (state.apkPath != null) R.string.update_install else R.string.update_download))
            }
            TextButton(onClick = viewModel::skipCurrent) { Text(stringResource(R.string.update_skip)) }
        }
    }
}

@Composable
private fun SkippedSection(state: UpdateUiState, update: AvailableUpdate, viewModel: UpdateViewModel) {
    SettingsGroup(title = null) {
        SettingsRow {
            Text(stringResource(R.string.update_skipped, update.versionName), style = MaterialTheme.typography.bodyLarge)
        }
        NavRow(Icons.Rounded.SystemUpdate, stringResource(R.string.update_unskip), stringResource(R.string.update_nav_available, update.versionName), viewModel::clearSkip)
    }
}

@Composable
private fun assetLine(update: AvailableUpdate, context: Context): String {
    val size = if (update.apkSize > 0) Formatter.formatShortFileSize(context, update.apkSize) else ""
    val date = update.publishedAt.take(10)
    return stringResource(R.string.update_asset_line, size, date)
}

private const val MaxNotesChars = 4000
