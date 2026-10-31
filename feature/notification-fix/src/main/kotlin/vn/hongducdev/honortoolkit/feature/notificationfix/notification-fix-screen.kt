package vn.hongducdev.honortoolkit.feature.notificationfix

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.ui.LetterAvatar
import vn.hongducdev.honortoolkit.core.ui.groupedRowShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import vn.hongducdev.honortoolkit.core.ui.SectionTitle
import vn.hongducdev.honortoolkit.core.ui.ToolkitSearchField
import androidx.compose.ui.semantics.Role
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationFixScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: NotificationFixViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val openFailed = stringResource(R.string.notification_fix_open_failed)

    val messageText = state.message?.let { messageText(it) }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    val selected = state.selected
    if (selected != null) BackHandler(onBack = viewModel::close)
    ProvideToolbar(
        ToolbarSpec.Page(
            title = selected?.label ?: stringResource(R.string.notification_fix_title),
            backDescription = stringResource(R.string.notification_fix_back),
            onBack = if (selected != null) viewModel::close else onBack,
            fab = ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.notification_fix_refresh)) {
                if (selected == null) viewModel.refresh() else viewModel.open(selected)
            },
        ),
    )

    fun openSettings(open: () -> Boolean) {
        if (!open()) scope.launch { snackbar.showSnackbar(openFailed) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loading) ToolkitLoadingIndicator()
            if (selected == null) {
                AppList(state, viewModel)
            } else {
                AppDetail(
                    state = state,
                    onApply = viewModel::applyFix,
                    onOpenLaunchManagement = { openSettings { NotificationFixIntents.openAppLaunchManagement(context) } },
                    onOpenBattery = { openSettings { NotificationFixIntents.openBatterySettings(context) } },
                )
            }
        }
    }
}

@Composable
private fun AppList(state: NotificationFixUiState, viewModel: NotificationFixViewModel) {
    ToolkitSearchField(state.query, viewModel::setQuery, stringResource(R.string.notification_fix_search))
    val visible = state.visibleApps
    if (visible.isEmpty() && !state.loading) Text(stringResource(R.string.notification_fix_empty), style = MaterialTheme.typography.bodyMedium)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = LocalToolbarClearance.current),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(visible, key = { _, app -> app.packageName }) { index, app ->
            ListItem(
                modifier = Modifier.clip(groupedRowShape(index, visible.lastIndex)).clickable(role = Role.Button) { viewModel.open(app) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                leadingContent = { LetterAvatar(app.label) },
                headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text(app.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            )
        }
    }
}

@Composable
private fun AppDetail(state: NotificationFixUiState, onApply: () -> Unit, onOpenLaunchManagement: () -> Unit, onOpenBattery: () -> Unit) {
    val steps = state.steps ?: return
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalToolbarClearance.current)) {
        items(steps, key = { it.step }) { StepRow(it) }
        item {
            FilledTonalButton(onClick = onApply, enabled = state.canApply, modifier = Modifier.padding(vertical = 12.dp)) {
                Text(stringResource(if (state.canApply) R.string.notification_fix_apply else R.string.notification_fix_all_done))
            }
        }
        item { SectionTitle(stringResource(R.string.notification_fix_manual_title)) }
        item { Text(stringResource(R.string.notification_fix_manual_desc), style = MaterialTheme.typography.bodySmall) }
        item { TextButton(onClick = onOpenLaunchManagement) { Text(stringResource(R.string.notification_fix_open_launch)) } }
        item { TextButton(onClick = onOpenBattery) { Text(stringResource(R.string.notification_fix_open_battery)) } }
    }
}

@Composable
private fun StepRow(state: StepState) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(stepTitle(state.step))) },
        supportingContent = { Text(stringResource(stepDescription(state.step)), style = MaterialTheme.typography.bodySmall) },
        trailingContent = {
            Text(
                stringResource(statusText(state.status)),
                style = MaterialTheme.typography.labelLarge,
                color = if (state.status == StepStatus.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        },
    )
}

private fun stepTitle(step: FixStep): Int = when (step) {
    FixStep.Notifications -> R.string.notification_fix_step_notifications
    FixStep.DozeWhitelist -> R.string.notification_fix_step_doze
    FixStep.BackgroundRun -> R.string.notification_fix_step_background
    FixStep.ExactAlarm -> R.string.notification_fix_step_alarm
    FixStep.StandbyBucket -> R.string.notification_fix_step_standby
}

private fun stepDescription(step: FixStep): Int = when (step) {
    FixStep.Notifications -> R.string.notification_fix_step_notifications_desc
    FixStep.DozeWhitelist -> R.string.notification_fix_step_doze_desc
    FixStep.BackgroundRun -> R.string.notification_fix_step_background_desc
    FixStep.ExactAlarm -> R.string.notification_fix_step_alarm_desc
    FixStep.StandbyBucket -> R.string.notification_fix_step_standby_desc
}

private fun statusText(status: StepStatus): Int = when (status) {
    StepStatus.Satisfied -> R.string.notification_fix_status_on
    StepStatus.Missing -> R.string.notification_fix_status_off
    StepStatus.NotApplicable -> R.string.notification_fix_status_na
    StepStatus.Failed -> R.string.notification_fix_status_failed
}

@Composable
private fun messageText(message: FixMessage): String = stringResource(
    when (message) {
        FixMessage.Applied -> R.string.notification_fix_msg_applied
        FixMessage.PartiallyApplied -> R.string.notification_fix_msg_partial
        FixMessage.ShellFailed -> R.string.notification_fix_msg_shell_failed
    },
)
