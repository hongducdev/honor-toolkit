package vn.hongducdev.honortoolkit.feature.gmscheck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GmsCheckScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: GmsCheckViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.gms_check_title),
            backDescription = stringResource(R.string.gms_check_back),
            onBack = onBack,
            fab = ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.gms_check_refresh), viewModel::refresh),
        ),
    )

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.loading) ToolkitLoadingIndicator()
            if (state.failed) Text(stringResource(R.string.gms_check_failed), style = MaterialTheme.typography.bodyMedium)
            state.report?.let { report ->
                LazyColumn(contentPadding = PaddingValues(bottom = LocalToolbarClearance.current), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item { Text(stringResource(if (report.isReady) R.string.gms_check_ready else R.string.gms_check_not_ready), style = MaterialTheme.typography.titleMedium) }
                    item {
                        SettingsGroup(title = null) {
                            report.packages.forEach { PackageRow(it) }
                            AccountRow(report.googleAccounts)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PackageRow(state: GmsPackageState) {
    SettingsRow {
        Text(stringResource(titleOf(state.target)), style = MaterialTheme.typography.titleSmall)
        Text(stringResource(statusOf(state.status), state.version.orEmpty()), style = MaterialTheme.typography.bodyMedium)
        adviceOf(state.status)?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AccountRow(accounts: Int?) {
    SettingsRow {
        Text(stringResource(R.string.gms_check_account), style = MaterialTheme.typography.titleSmall)
        Text(
            when {
                accounts == null -> stringResource(R.string.gms_check_account_unknown)
                accounts == 0 -> stringResource(R.string.gms_check_account_none)
                else -> stringResource(R.string.gms_check_account_count, accounts)
            },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun titleOf(target: GmsPackage): Int = when (target) {
    GmsPackage.PlayServices -> R.string.gms_check_play_services
    GmsPackage.ServicesFramework -> R.string.gms_check_framework
    GmsPackage.PlayStore -> R.string.gms_check_play_store
}

private fun statusOf(status: GmsPackageStatus): Int = when (status) {
    GmsPackageStatus.Missing -> R.string.gms_check_status_missing
    GmsPackageStatus.Disabled -> R.string.gms_check_status_disabled
    GmsPackageStatus.Enabled -> R.string.gms_check_status_enabled
}

private fun adviceOf(status: GmsPackageStatus): Int? = when (status) {
    GmsPackageStatus.Missing -> R.string.gms_check_advice_missing
    GmsPackageStatus.Disabled -> R.string.gms_check_advice_disabled
    GmsPackageStatus.Enabled -> null
}
