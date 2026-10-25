package vn.hongducdev.honortoolkit.feature.debloat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.CookieShape
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Refresh
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebloatScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DebloatViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val text = viewModel.exportText()
            context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
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

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.debloat_title),
            backDescription = stringResource(R.string.debloat_back),
            onBack = onBack,
            actions = listOf(
                ToolbarAction(Icons.Rounded.FileDownload, stringResource(R.string.debloat_export)) { exportLauncher.launch("honor-toolkit-debloat.txt") },
            ),
            fab = ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.debloat_refresh), viewModel::refresh),
        ),
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                label = { Text(stringResource(R.string.debloat_search)) },
                singleLine = true,
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.filter == DebloatFilter.Enabled,
                    onClick = { viewModel.setFilter(DebloatFilter.Enabled) },
                    label = { Text(stringResource(R.string.debloat_filter_enabled)) },
                )
                FilterChip(
                    selected = state.filter == DebloatFilter.Disabled,
                    onClick = { viewModel.setFilter(DebloatFilter.Disabled) },
                    label = { Text(stringResource(R.string.debloat_filter_disabled)) },
                )
            }
            if (state.loading) ToolkitLoadingIndicator()
            val visible = state.visible
            if (visible.isEmpty() && !state.loading) {
                Text(stringResource(R.string.debloat_empty), style = MaterialTheme.typography.bodyMedium)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalToolbarClearance.current)) {
                    items(visible, key = { it.packageName }) { entry ->
                        PackageRow(entry, onDisable = { viewModel.requestDisable(entry) }, onEnable = { viewModel.enable(entry) })
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                        .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, Color.Transparent))),
                )
            }
        }
    }

    state.pendingDisable?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDisable,
            title = { Text(stringResource(R.string.debloat_confirm_title, entry.label)) },
            text = { Text(stringResource(R.string.debloat_confirm_body)) },
            confirmButton = { Button(onClick = viewModel::confirmDisable) { Text(stringResource(R.string.debloat_confirm_ok)) } },
            dismissButton = { OutlinedButton(onClick = viewModel::cancelDisable) { Text(stringResource(R.string.debloat_confirm_cancel)) } },
        )
    }
}

@Composable
private fun PackageRow(entry: PackageEntry, onDisable: () -> Unit, onEnable: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val (badgeContainer, badgeContent) = when {
        entry.protection != null -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        entry.verified -> scheme.primaryContainer to scheme.onPrimaryContainer
        else -> scheme.surfaceContainerHighest to scheme.onSurfaceVariant
    }
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = {
            Box(
                modifier = Modifier.size(44.dp).background(scheme.secondaryContainer, CookieShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    entry.label.firstOrNull()?.uppercase().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = scheme.onSecondaryContainer,
                )
            }
        },
        headlineContent = { Text(entry.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(entry.packageName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    statusText(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = badgeContent,
                    modifier = Modifier.background(badgeContainer, CircleShape).padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        },
        trailingContent = {
            when {
                !entry.enabled -> OutlinedButton(onClick = onEnable) { Text(stringResource(R.string.debloat_enable)) }
                entry.protection == null -> FilledTonalButton(onClick = onDisable) { Text(stringResource(R.string.debloat_disable)) }
            }
        },
    )
}

@Composable
private fun statusText(entry: PackageEntry): String = when {
    entry.protection == ProtectionReason.CoreSystem -> stringResource(R.string.debloat_protected_core)
    entry.protection == ProtectionReason.InUse -> stringResource(R.string.debloat_protected_in_use)
    entry.verified -> stringResource(R.string.debloat_verified)
    else -> stringResource(R.string.debloat_not_assessed)
}

@Composable
private fun messageText(message: DebloatMessage): String = when (message) {
    is DebloatMessage.Disabled -> stringResource(R.string.debloat_msg_disabled, message.label)
    is DebloatMessage.Enabled -> stringResource(R.string.debloat_msg_enabled, message.label)
    is DebloatMessage.Blocked -> stringResource(
        if (message.reason == ProtectionReason.CoreSystem) R.string.debloat_protected_core else R.string.debloat_protected_in_use,
    )
    is DebloatMessage.Failed -> stringResource(R.string.debloat_msg_failed, message.output)
    DebloatMessage.ScanFailed -> stringResource(R.string.debloat_msg_scan_failed)
    DebloatMessage.Exported -> stringResource(R.string.debloat_msg_exported)
}
