package vn.hongducdev.honortoolkit.feature.notificationfix

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.ToolkitTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationFixScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.notification_fix_open_failed)

    fun runAction(action: () -> Boolean) {
        if (!action()) scope.launch { snackbar.showSnackbar(failedMessage) }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            ToolkitTopBar(
                title = stringResource(R.string.notification_fix_title),
                onBack = onBack,
                backDescription = stringResource(R.string.notification_fix_back),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()).padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.notification_fix_intro), style = MaterialTheme.typography.bodyMedium)

            Step(
                titleRes = R.string.notification_fix_step1_title,
                descriptionRes = R.string.notification_fix_step1_desc,
                actionLabelRes = R.string.notification_fix_step1_action,
                onAction = { runAction { NotificationFixIntents.openNotificationSettings(context) } },
            )
            Step(
                titleRes = R.string.notification_fix_step2_title,
                descriptionRes = R.string.notification_fix_step2_desc,
                actionLabelRes = R.string.notification_fix_step2_action,
                onAction = { runAction { NotificationFixIntents.openBatterySettings(context) } },
            )
            Step(
                titleRes = R.string.notification_fix_step3_title,
                descriptionRes = R.string.notification_fix_step3_desc,
                actionLabelRes = R.string.notification_fix_step3_action,
                onAction = { runAction { NotificationFixIntents.openAppLaunchManagement(context) } },
            )
            Step(
                titleRes = R.string.notification_fix_step4_title,
                descriptionRes = R.string.notification_fix_step4_desc,
                actionLabelRes = R.string.notification_fix_step4_action,
                onAction = { runAction { NotificationFixIntents.openBatteryOptimizationSettings(context) } },
            )

            Text(
                stringResource(R.string.notification_fix_source),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Step(titleRes: Int, descriptionRes: Int, actionLabelRes: Int, onAction: () -> Unit) {
    SettingsGroup(title = null) {
        SettingsRow {
            Text(stringResource(titleRes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(descriptionRes), style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = onAction) { Text(stringResource(actionLabelRes)) }
        }
    }
}
