package vn.hongducdev.honortoolkit.feature.automation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<AutomationRule?>(null) }
    val editing = state.editor

    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    // Permissions can be changed in Settings; reload the rule runnability when the page returns.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    BackHandler(enabled = editing != null) { viewModel.closeEditor() }

    ProvideToolbar(
        ToolbarSpec.Page(
            title = when {
                editing == null -> stringResource(R.string.automation_title)
                editing.name.isBlank() -> stringResource(R.string.automation_editor_new)
                else -> editing.name
            },
            backDescription = stringResource(R.string.automation_back),
            onBack = if (editing != null) viewModel::closeEditor else onBack,
            actions = if (editing == null) {
                listOf(
                    ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.automation_refresh), viewModel::refresh),
                    ToolbarAction(Icons.AutoMirrored.Rounded.Undo, stringResource(R.string.automation_undo), viewModel::undoLastWrite),
                )
            } else {
                emptyList()
            },
            fab = if (editing == null) {
                ToolbarAction(Icons.Rounded.Add, stringResource(R.string.automation_add), viewModel::startNew)
            } else {
                null
            },
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = LocalToolbarClearance.current)) },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (editing != null) {
                AutomationEditor(
                    rule = editing,
                    onSave = viewModel::save,
                    onCancel = viewModel::closeEditor,
                )
            } else {
                AutomationRuleList(
                    state = state,
                    onToggle = viewModel::toggle,
                    onEdit = viewModel::edit,
                    onDelete = { pendingDelete = it },
                    onClearLog = viewModel::clearLog,
                    onPermissionsChanged = viewModel::refresh,
                )
            }
        }
    }

    val deleting = pendingDelete
    if (deleting != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.automation_delete_title)) },
            text = { Text(stringResource(R.string.automation_delete_body, deleting.name)) },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = null
                    viewModel.delete(deleting)
                }) { Text(stringResource(R.string.automation_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.automation_cancel)) }
            },
        )
    }
}

@Composable
private fun AutomationRuleList(
    state: AutomationUiState,
    onToggle: (AutomationRule, Boolean) -> Unit,
    onEdit: (AutomationRule) -> Unit,
    onDelete: (AutomationRule) -> Unit,
    onClearLog: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionsChanged()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalToolbarClearance.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "intro") {
            Text(
                stringResource(R.string.automation_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.missing.isNotEmpty()) {
            item(key = "permissions") {
                SettingsGroup(title = stringResource(R.string.automation_perm_heading)) {
                    state.missing.forEach { requirement ->
                        PermissionRow(
                            requirement = requirement,
                            onOpen = {
                                val runtime = AutomationPermissions.runtimePermission(requirement)
                                if (runtime != null) {
                                    permissionLauncher.launch(runtime)
                                } else {
                                    AutomationPermissions.settingsIntent(context, requirement)?.let { intent ->
                                        runCatching { context.startActivity(intent) }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
        item(key = "rules") {
            SettingsGroup(title = stringResource(R.string.automation_rules)) {
                if (state.rules.isEmpty()) {
                    SettingsRow {
                        Text(stringResource(R.string.automation_no_rules), style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    state.rules.forEach { rule ->
                        RuleRow(
                            rule = rule,
                            live = state.isLive(rule),
                            blocked = state.missingByRule[rule.id].orEmpty().isNotEmpty(),
                            onToggle = { onToggle(rule, it) },
                            onEdit = { onEdit(rule) },
                            onDelete = { onDelete(rule) },
                        )
                    }
                }
            }
        }
        item(key = "log") {
            SettingsGroup(title = stringResource(R.string.automation_log)) {
                SettingsRow {
                    Text(
                        state.log.ifBlank { stringResource(R.string.automation_log_empty) },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        stringResource(R.string.automation_log_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onClearLog, enabled = state.log.isNotBlank()) {
                        Text(stringResource(R.string.automation_log_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleRow(
    rule: AutomationRule,
    live: Boolean,
    blocked: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    SettingsRow {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    rule.trigger.summary(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rule.actions.joinToString(" · ") { it.summary() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = live, onCheckedChange = onToggle)
        }
        if (blocked) {
            Text(
                stringResource(R.string.automation_rule_disabled_permission),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onEdit) { Text(stringResource(R.string.automation_edit)) }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.automation_delete), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PermissionRow(requirement: AutomationRequirement, onOpen: () -> Unit) {
    SettingsRow {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(requirementLabel(requirement)), style = MaterialTheme.typography.bodyLarge)
                Text(
                    stringResource(requirementDescription(requirement)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) {
                Text(
                    stringResource(
                        if (AutomationPermissions.runtimePermission(requirement) != null) {
                            R.string.automation_perm_grant
                        } else {
                            R.string.automation_perm_open_settings
                        },
                    ),
                )
            }
        }
    }
}

private fun requirementLabel(requirement: AutomationRequirement): Int = when (requirement) {
    AutomationRequirement.Accessibility -> R.string.automation_perm_accessibility
    AutomationRequirement.NotificationAccess -> R.string.automation_perm_notifications
    AutomationRequirement.Location -> R.string.automation_perm_location
    AutomationRequirement.Overlay -> R.string.automation_perm_overlay
    AutomationRequirement.PostNotifications -> R.string.automation_perm_post_notifications
    AutomationRequirement.BluetoothConnect -> R.string.automation_perm_bluetooth
    AutomationRequirement.ExactAlarm -> R.string.automation_perm_exact_alarm
    AutomationRequirement.DndAccess -> R.string.automation_perm_dnd
}

private fun requirementDescription(requirement: AutomationRequirement): Int = when (requirement) {
    AutomationRequirement.Accessibility -> R.string.automation_perm_accessibility_desc
    AutomationRequirement.NotificationAccess -> R.string.automation_perm_notifications_desc
    AutomationRequirement.Location -> R.string.automation_perm_location_desc
    AutomationRequirement.Overlay -> R.string.automation_perm_overlay_desc
    AutomationRequirement.PostNotifications -> R.string.automation_perm_post_notifications_desc
    AutomationRequirement.BluetoothConnect -> R.string.automation_perm_bluetooth_desc
    AutomationRequirement.ExactAlarm -> R.string.automation_perm_exact_alarm_desc
    AutomationRequirement.DndAccess -> R.string.automation_perm_dnd_desc
}

@Composable
private fun messageText(message: AutomationMessage): String = when (message) {
    AutomationMessage.Saved -> stringResource(R.string.automation_msg_saved)
    AutomationMessage.Deleted -> stringResource(R.string.automation_msg_deleted)
    is AutomationMessage.Invalid -> stringResource(R.string.automation_msg_invalid, message.text)
    is AutomationMessage.Undone -> stringResource(R.string.automation_msg_undone, message.count)
    AutomationMessage.UndoEmpty -> stringResource(R.string.automation_msg_undo_empty)
    is AutomationMessage.UndoFailed -> stringResource(R.string.automation_msg_undo_failed, message.failed)
    AutomationMessage.MissingPermission -> stringResource(R.string.automation_msg_missing_permission)
}
