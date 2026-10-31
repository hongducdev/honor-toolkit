package vn.hongducdev.honortoolkit.feature.circlesearch

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

/** Top-level tab: assistant status plus the list of settings pages, each opened by the host through [onOpenPage]. */
@Composable
fun CircleSearchScreen(onOpenPage: (CsPage) -> Unit, modifier: Modifier = Modifier, viewModel: CircleSearchViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

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

    ProvideToolbar(ToolbarSpec.Tabs())

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbar, modifier = Modifier.padding(bottom = LocalToolbarClearance.current)) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp).verticalScroll(rememberScrollState()).padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(state, viewModel)
            SettingsGroup(null) {
                CsPage.entries.forEach { target ->
                    NavRow(target.icon, stringResource(target.titleRes), stringResource(target.descriptionRes)) { onOpenPage(target) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(state: CircleSearchUiState, viewModel: CircleSearchViewModel) {
    val scheme = MaterialTheme.colorScheme
    val (container, content) = when (state.readiness) {
        Readiness.Ready -> scheme.primaryContainer to scheme.onPrimaryContainer
        Readiness.NotAssistant -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        Readiness.NoGoogle -> scheme.errorContainer to scheme.onErrorContainer
    }
    val showSetAssistant = state.readiness == Readiness.NotAssistant
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
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
            if (state.busy) ToolkitLoadingIndicator()
            if (showSetAssistant || state.canRestore) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (showSetAssistant) {
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
}

@Composable
private fun messageText(message: CircleSearchMessage): String = when (message) {
    CircleSearchMessage.AssistantSet -> stringResource(R.string.cs_assistant_done)
    CircleSearchMessage.AssistantRestored -> stringResource(R.string.cs_assistant_restored)
    CircleSearchMessage.NothingToRestore -> stringResource(R.string.cs_assistant_nothing_to_restore)
    CircleSearchMessage.NeedsAdb -> stringResource(R.string.cs_assistant_needs_adb)
    is CircleSearchMessage.AssistantFailed -> stringResource(R.string.cs_assistant_failed, message.reason)
}
