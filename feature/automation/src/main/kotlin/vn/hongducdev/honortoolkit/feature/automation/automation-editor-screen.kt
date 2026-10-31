package vn.hongducdev.honortoolkit.feature.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow

/** Which list of the rule an action sheet is editing, and which row (null = a new action). */
private data class ActionTarget(val exit: Boolean, val index: Int?)

/**
 * Rule editor. It keeps a local copy and only hands it to the view model on "Lưu", so a half-typed
 * rule never reaches the service. A rule on a state has a second action list that runs on exit.
 */
@Composable
fun AutomationEditor(
    rule: AutomationRule,
    onSave: (AutomationRule) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember(rule.id) { mutableStateOf(rule.name) }
    var enabled by remember(rule.id) { mutableStateOf(rule.enabled) }
    var trigger by remember(rule.id) { mutableStateOf(rule.trigger) }
    var actions by remember(rule.id) { mutableStateOf(rule.actions) }
    var exitActions by remember(rule.id) { mutableStateOf(rule.exitActions) }
    var pickingKind by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ActionTarget?>(null) }
    val isState = trigger is Trigger.InState

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalToolbarClearance.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "name") {
            SettingsGroup(title = null) {
                SettingsRow {
                    Field(
                        label = stringResource(R.string.automation_editor_name),
                        value = name,
                        onChange = { name = it },
                    )
                }
                SwitchRow(
                    label = stringResource(R.string.automation_editor_enabled),
                    checked = enabled,
                    onChange = { enabled = it },
                )
            }
        }
        item(key = "trigger") {
            SettingsGroup(
                title = stringResource(
                    if (isState) R.string.automation_editor_state else R.string.automation_editor_trigger,
                ),
            ) {
                SettingsRow {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KindBadge(trigger.icon())
                        Text(trigger.summary(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickingKind = true }) {
                            Text(stringResource(R.string.automation_editor_change))
                        }
                    }
                    TriggerFields(trigger) { trigger = it }
                }
            }
        }
        item(key = "actions") {
            ActionList(
                title = stringResource(if (isState) R.string.automation_editor_on_enter else R.string.automation_editor_actions),
                emptyText = stringResource(R.string.automation_editor_no_actions),
                actions = actions,
                onEdit = { editing = ActionTarget(exit = false, index = it) },
                onRemove = { index -> actions = actions.filterIndexed { i, _ -> i != index } },
            )
        }
        if (isState) {
            item(key = "exit") {
                ActionList(
                    title = stringResource(R.string.automation_editor_on_exit),
                    emptyText = stringResource(R.string.automation_editor_no_exit_actions),
                    actions = exitActions,
                    onEdit = { editing = ActionTarget(exit = true, index = it) },
                    onRemove = { index -> exitActions = exitActions.filterIndexed { i, _ -> i != index } },
                )
            }
        }
        item(key = "save") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        onSave(
                            rule.copy(
                                name = name.trim(),
                                enabled = enabled,
                                trigger = trigger,
                                actions = actions,
                                exitActions = if (isState) exitActions else emptyList(),
                            ),
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.automation_save))
                }
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.automation_cancel))
                }
            }
        }
    }

    if (pickingKind) {
        TriggerKindSheet(
            stateKinds = isState,
            onPick = {
                trigger = it
                pickingKind = false
            },
            onDismiss = { pickingKind = false },
        )
    }

    editing?.let { target ->
        val list = if (target.exit) exitActions else actions
        ActionSheet(
            initial = target.index?.let { list.getOrNull(it) },
            onSave = { action ->
                val updated = if (target.index == null) list + action else list.mapIndexed { i, old -> if (i == target.index) action else old }
                if (target.exit) exitActions = updated else actions = updated
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun ActionList(
    title: String,
    emptyText: String,
    actions: List<Action>,
    onEdit: (Int?) -> Unit,
    onRemove: (Int) -> Unit,
) {
    SettingsGroup(title = title) {
        if (actions.isEmpty()) {
            SettingsRow {
                Text(emptyText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions.forEachIndexed { index, action ->
            SettingsRow(modifier = Modifier.clickable { onEdit(index) }) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    KindBadge(action.icon(), tertiary = true)
                    Text(action.summary(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.automation_editor_remove_action),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
        SettingsRow {
            Button(onClick = { onEdit(null) }) { Text(stringResource(R.string.automation_editor_add_action)) }
        }
    }
}
