package vn.hongducdev.honortoolkit.feature.automation

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
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
import vn.hongducdev.honortoolkit.core.ui.groupedRowShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AutomationViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<List<AutomationRule>>(emptyList()) }
    var choosingKind by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    val editing = state.editor

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    val message = state.message?.let { messageText(it) }
    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.rules) {
        selectedIds = selectedIds intersect state.rules.map { it.id }.toSet()
        if (state.rules.isEmpty()) exitSelection()
    }
    // Permissions can be changed in Settings; reload the rule runnability when the page returns.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    BackHandler(enabled = editing != null || selectionMode) {
        if (editing != null) viewModel.closeEditor() else exitSelection()
    }

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
                ToolbarAction(Icons.Rounded.Add, stringResource(R.string.automation_add)) { choosingKind = true }
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
                AutomationEditor(rule = editing, onSave = viewModel::save, onCancel = viewModel::closeEditor)
            } else {
                AutomationRuleList(
                    state = state,
                    selectionMode = selectionMode,
                    selectedIds = selectedIds,
                    onSelectedChange = { id, checked ->
                        selectionMode = true
                        selectedIds = if (checked) selectedIds + id else selectedIds - id
                    },
                    onExitSelection = ::exitSelection,
                    onToggle = { rule -> viewModel.toggle(rule, !rule.enabled) },
                    onEdit = viewModel::edit,
                    onTest = viewModel::test,
                    onDelete = { pendingDelete = listOf(it) },
                    onSetEnabledMany = viewModel::setEnabledMany,
                    onDeleteMany = { ids -> pendingDelete = state.rules.filter { it.id in ids } },
                    onNew = { choosingKind = true },
                    onClearLog = viewModel::clearLog,
                    onPermissionsChanged = viewModel::refresh,
                )
            }
        }
    }

    if (choosingKind) {
        NewRuleSheet(
            onPick = { stateRule ->
                choosingKind = false
                viewModel.startNew(state = stateRule)
            },
            onDismiss = { choosingKind = false },
        )
    }

    if (pendingDelete.isNotEmpty()) {
        val doomed = pendingDelete
        AlertDialog(
            onDismissRequest = { pendingDelete = emptyList() },
            title = { Text(stringResource(R.string.automation_delete_title)) },
            text = {
                Text(
                    if (doomed.size == 1) {
                        stringResource(R.string.automation_delete_body, doomed.single().name)
                    } else {
                        stringResource(R.string.automation_selected_count, doomed.size)
                    },
                )
            },
            confirmButton = {
                Button(onClick = {
                    pendingDelete = emptyList()
                    if (doomed.size == 1) viewModel.delete(doomed.single()) else viewModel.deleteMany(doomed.map { it.id }.toSet())
                    exitSelection()
                }) { Text(stringResource(R.string.automation_delete)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDelete = emptyList() }) { Text(stringResource(R.string.automation_cancel)) }
            },
        )
    }
}

@Composable
private fun AutomationRuleList(
    state: AutomationUiState,
    selectionMode: Boolean,
    selectedIds: Set<String>,
    onSelectedChange: (String, Boolean) -> Unit,
    onExitSelection: () -> Unit,
    onToggle: (AutomationRule) -> Unit,
    onEdit: (AutomationRule) -> Unit,
    onTest: (AutomationRule) -> Unit,
    onDelete: (AutomationRule) -> Unit,
    onSetEnabledMany: (Set<String>, Boolean) -> Unit,
    onDeleteMany: (Set<String>) -> Unit,
    onNew: () -> Unit,
    onClearLog: () -> Unit,
    onPermissionsChanged: () -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        onPermissionsChanged()
    }
    val (liveRules, pausedRules) = state.rules.partition { state.isLive(it) }
    var pausedOpen by rememberSaveable { mutableStateOf(false) }
    val chevron by animateFloatAsState(if (pausedOpen) 180f else 0f, label = "paused_chevron")

    @Composable
    fun RuleGroup(rules: List<AutomationRule>) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rules.forEachIndexed { index, rule ->
                RuleCard(
                    rule = rule,
                    live = state.isLive(rule),
                    blocked = rule.enabled && state.missingByRule[rule.id].orEmpty().isNotEmpty(),
                    shape = groupedRowShape(index, rules.lastIndex, outer = 28.dp),
                    selectionMode = selectionMode,
                    selected = rule.id in selectedIds,
                    callbacks = RuleCardCallbacks(
                        onClick = { onEdit(rule) },
                        onToggle = { onToggle(rule) },
                        onTest = { onTest(rule) },
                        onDelete = { onDelete(rule) },
                        onSelectedChange = { onSelectedChange(rule.id, it) },
                    ),
                )
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalToolbarClearance.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
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
        item(key = "selection") {
            AnimatedVisibility(
                visible = selectionMode,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                SelectionBar(
                    count = selectedIds.size,
                    onCancel = onExitSelection,
                    onEnable = { onSetEnabledMany(selectedIds, true) },
                    onDisable = { onSetEnabledMany(selectedIds, false) },
                    onDelete = { onDeleteMany(selectedIds) },
                )
            }
        }
        if (state.rules.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.automation_empty_title), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onNew) { Text(stringResource(R.string.automation_empty_action)) }
                }
            }
        }
        if (liveRules.isNotEmpty()) {
            item(key = "live") { RuleGroup(liveRules) }
        }
        if (pausedRules.isNotEmpty()) {
            item(key = "paused") {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.extraLarge)
                            .clickable { pausedOpen = !pausedOpen }
                            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.automation_disabled_count, pausedRules.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.graphicsLayer { rotationZ = chevron },
                        )
                    }
                    AnimatedVisibility(
                        visible = pausedOpen,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) { RuleGroup(pausedRules) }
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
                    TextButton(onClick = onClearLog, enabled = state.log.isNotBlank()) {
                        Text(stringResource(R.string.automation_log_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onCancel: () -> Unit, onEnable: () -> Unit, onDisable: () -> Unit, onDelete: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.automation_selected_count, count),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp),
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.automation_cancel))
            }
            FilledTonalButton(onClick = onDisable, enabled = count > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.automation_disable))
            }
            FilledTonalButton(onClick = onEnable, enabled = count > 0, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.automation_enable))
            }
            FilledTonalButton(onClick = onDelete, enabled = count > 0, modifier = Modifier.weight(1f)) {
                Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.automation_delete))
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
    AutomationRequirement.Calendar -> R.string.automation_perm_calendar
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
    AutomationRequirement.Calendar -> R.string.automation_perm_calendar_desc
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
    is AutomationMessage.Tested -> stringResource(R.string.automation_msg_tested, message.name)
}
