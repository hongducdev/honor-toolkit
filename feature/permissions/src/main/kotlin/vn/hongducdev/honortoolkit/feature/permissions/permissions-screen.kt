package vn.hongducdev.honortoolkit.feature.permissions

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.LetterAvatar
import vn.hongducdev.honortoolkit.core.ui.groupedRowShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import vn.hongducdev.honortoolkit.core.ui.SectionTitle
import vn.hongducdev.honortoolkit.core.ui.ToolkitSearchField
import androidx.compose.ui.semantics.Role
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: PermissionsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            context.contentResolver.openOutputStream(uri)?.use { it.write(viewModel.exportText().toByteArray()) }
            viewModel.reportExported()
        }
    }

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
            title = selected?.label ?: stringResource(R.string.permissions_title),
            backDescription = stringResource(R.string.permissions_back),
            onBack = if (selected != null) viewModel::close else onBack,
            actions = listOf(
                ToolbarAction(Icons.Rounded.FileDownload, stringResource(R.string.permissions_export)) { exportLauncher.launch("honor-toolkit-permissions.txt") },
            ),
            fab = ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.permissions_refresh)) {
                if (selected == null) viewModel.refresh() else viewModel.open(selected)
            },
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loading) ToolkitLoadingIndicator()
            if (selected == null) AppList(state, viewModel) else AppDetail(state, viewModel)
        }
    }
}

@Composable
private fun AppList(state: PermissionsUiState, viewModel: PermissionsViewModel) {
    ToolkitSearchField(state.query, viewModel::setQuery, stringResource(R.string.permissions_search))
    val visible = state.visibleApps
    if (visible.isEmpty() && !state.loading) Text(stringResource(R.string.permissions_empty), style = MaterialTheme.typography.bodyMedium)
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
private fun AppDetail(state: PermissionsUiState, viewModel: PermissionsViewModel) {
    val detail = state.detail ?: return
    val blocked = detail.blocked
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalToolbarClearance.current)) {
        if (blocked != null) {
            item { Text(blockText(blocked), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        }
        item { SectionTitle(stringResource(R.string.permissions_group_runtime)) }
        if (detail.permissions.isEmpty()) item { Text(stringResource(R.string.permissions_none), style = MaterialTheme.typography.bodyMedium) }
        items(detail.permissions, key = { it.name }) { entry ->
            ListItem(
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                headlineContent = { Text(state.permissionLabels[entry.name] ?: entry.name) },
                supportingContent = {
                    Text(
                        if (entry.fixed) stringResource(R.string.permissions_fixed) else entry.name,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingContent = {
                    Switch(
                        checked = entry.granted,
                        onCheckedChange = { viewModel.setGranted(entry.name, it) },
                        enabled = blocked == null && !entry.fixed,
                    )
                },
            )
        }
        item {
            TextButton(onClick = viewModel::toggleAdvanced) { Text(stringResource(R.string.permissions_advanced)) }
        }
        if (state.advancedOpen) {
            item { Text(stringResource(R.string.permissions_advanced_note), style = MaterialTheme.typography.bodySmall) }
            if (detail.appOps.isEmpty()) item { Text(stringResource(R.string.permissions_appops_none), style = MaterialTheme.typography.bodyMedium) }
            items(detail.appOps, key = { "op:" + it.name }) { op ->
                AppOpRow(op, enabled = blocked == null, onChange = { viewModel.setAppOp(op.name, it) })
            }
        }
    }
}

@Composable
private fun AppOpRow(op: AppOpEntry, enabled: Boolean, onChange: (AppOpMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(op.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Box {
                TextButton(onClick = { open = true }, enabled = enabled) { Text(modeText(op.mode)) }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    AppOpMode.entries.forEach { mode ->
                        DropdownMenuItem(text = { Text(modeText(mode)) }, onClick = { open = false; onChange(mode) })
                    }
                }
            }
        },
    )
}

@Composable
private fun modeText(mode: AppOpMode): String = stringResource(
    when (mode) {
        AppOpMode.Allow -> R.string.permissions_mode_allow
        AppOpMode.Ignore -> R.string.permissions_mode_ignore
        AppOpMode.Deny -> R.string.permissions_mode_deny
        AppOpMode.Foreground -> R.string.permissions_mode_foreground
        AppOpMode.Default -> R.string.permissions_mode_default
    },
)

@Composable
private fun blockText(reason: BlockReason): String = stringResource(
    when (reason) {
        BlockReason.InvalidName -> R.string.permissions_blocked_invalid
        BlockReason.NotUserApp -> R.string.permissions_blocked_not_user_app
        BlockReason.SharedUid -> R.string.permissions_blocked_shared_uid
        BlockReason.FixedBySystem -> R.string.permissions_blocked_fixed
        BlockReason.NotChangeable -> R.string.permissions_blocked_not_changeable
    },
)

@Composable
private fun messageText(message: PermissionsMessage): String = when (message) {
    PermissionsMessage.Changed -> stringResource(R.string.permissions_msg_changed)
    is PermissionsMessage.Blocked -> blockText(message.reason)
    is PermissionsMessage.Failed -> stringResource(R.string.permissions_msg_failed, message.output)
    PermissionsMessage.ScanFailed -> stringResource(R.string.permissions_msg_scan_failed)
    PermissionsMessage.Exported -> stringResource(R.string.permissions_msg_exported)
}
