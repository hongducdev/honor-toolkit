package vn.hongducdev.honortoolkit.feature.automation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.ToggleOn
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState

/** The rounded icon square used on every card and editor row. */
@Composable
internal fun KindBadge(icon: ImageVector, tertiary: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (tertiary) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (tertiary) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun KindTile(icon: ImageVector, label: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.width(104.dp).clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KindBadge(icon)
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun TriggerKindSheet(stateKinds: Boolean, onPick: (Trigger) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(if (stateKinds) R.string.automation_editor_choose_state else R.string.automation_editor_choose_trigger),
                style = MaterialTheme.typography.titleLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            ) {
                if (stateKinds) {
                    StateKind.entries.forEach { kind ->
                        KindTile(kind.icon, stringResource(kind.label)) { onPick(Trigger.InState(kind.create())) }
                    }
                } else {
                    TriggerKind.entries.forEach { kind ->
                        KindTile(kind.icon, stringResource(kind.label)) { onPick(kind.create()) }
                    }
                }
            }
        }
    }
}

/** Two steps: pick the kind of action, then fill its fields. Opening on an existing action edits it. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ActionSheet(initial: Action?, onSave: (Action) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    val adbState by AdbSession.get(LocalContext.current).state.collectAsState()
    val adbConnected = adbState == AdbState.Connected
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val current = draft
            if (current == null) {
                Text(stringResource(R.string.automation_editor_choose_action), style = MaterialTheme.typography.titleLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                ) {
                    ActionKind.entries.filter { !it.needsAdb || adbConnected }.forEach { kind ->
                        KindTile(kind.icon, stringResource(kind.label)) { draft = kind.create() }
                    }
                }
            } else {
                Text(stringResource(ActionKind.of(current).label), style = MaterialTheme.typography.titleLarge)
                ActionFields(current) { draft = it }
                Column(modifier = Modifier.padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(current) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.automation_editor_add_short))
                    }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.automation_cancel))
                    }
                }
            }
        }
    }
}

/** Chooses between a rule on an event and a rule on a condition that holds for a while. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NewRuleSheet(onPick: (state: Boolean) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(stringResource(R.string.automation_new_title), style = MaterialTheme.typography.titleLarge)
            NewRuleOption(
                icon = Icons.Rounded.Bolt,
                title = stringResource(R.string.automation_new_trigger),
                description = stringResource(R.string.automation_new_trigger_desc),
                onClick = { onPick(false) },
            )
            NewRuleOption(
                icon = Icons.Rounded.ToggleOn,
                title = stringResource(R.string.automation_new_state),
                description = stringResource(R.string.automation_new_state_desc),
                onClick = { onPick(true) },
            )
            androidx.compose.foundation.layout.Spacer(Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun NewRuleOption(icon: ImageVector, title: String, description: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KindBadge(icon)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
