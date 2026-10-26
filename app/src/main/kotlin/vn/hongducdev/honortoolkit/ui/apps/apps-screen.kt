package vn.hongducdev.honortoolkit.ui.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.ui.NavRow
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup

/** Entry point for the ADB-backed app tools, grouped here so the bottom toolbar stays short. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    onOpenNotificationFix: () -> Unit,
    onOpenDebloat: () -> Unit,
    onOpenPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp).padding(top = 8.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsGroup(title = null) {
                NavRow(
                    Icons.Rounded.Notifications,
                    stringResource(R.string.home_open_notification_fix),
                    stringResource(R.string.home_notification_fix_description),
                    onOpenNotificationFix,
                )
                NavRow(
                    Icons.Rounded.DeleteSweep,
                    stringResource(R.string.home_open_debloat),
                    stringResource(R.string.home_debloat_description),
                    onOpenDebloat,
                )
                NavRow(
                    Icons.Rounded.Lock,
                    stringResource(R.string.home_open_permissions),
                    stringResource(R.string.home_permissions_description),
                    onOpenPermissions,
                )
            }
        }
    }
}
