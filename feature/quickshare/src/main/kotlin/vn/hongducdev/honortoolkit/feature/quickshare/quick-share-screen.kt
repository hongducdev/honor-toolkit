package vn.hongducdev.honortoolkit.feature.quickshare

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.core.adb.AdbSession
import vn.hongducdev.honortoolkit.core.adb.AdbState
import vn.hongducdev.honortoolkit.core.ui.NavRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.material3.MaterialTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.SettingsGroup
import vn.hongducdev.honortoolkit.core.ui.SettingsNote
import vn.hongducdev.honortoolkit.core.ui.SettingsRow
import vn.hongducdev.honortoolkit.core.ui.SwitchRow
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec

@Composable
fun QuickShareScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val status by QuickShareReceiverState.status.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()

    fun setReceiving(on: Boolean) {
        if (on) {
            QuickShareReceiverService.start(context)
            // MagicOS rejects Magic Capsule requests until this app op is allowed; only ADB can set it.
            scope.launch {
                val session = AdbSession.get(context)
                if (session.state.value == AdbState.Connected) {
                    session.run("cmd appops set ${context.packageName} POST_PROMOTED_NOTIFICATIONS allow")
                }
            }
        } else {
            QuickShareReceiverService.stop(context)
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            context.startActivity(
                Intent(Intent.ACTION_SEND_MULTIPLE)
                    .setPackage(context.packageName)
                    .setType("*/*")
                    .putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris)),
            )
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { setReceiving(true) }

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.quick_share_title),
            backDescription = stringResource(R.string.quick_share_back),
            onBack = onBack,
        ),
    )

    Scaffold(modifier = modifier.fillMaxSize()) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = LocalToolbarClearance.current),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(null) {
                NavRow(Icons.Rounded.Share, stringResource(R.string.quick_share_pick_files), stringResource(R.string.quick_share_pick_files_description)) {
                    filePicker.launch(arrayOf("*/*"))
                }
            }
            SettingsGroup(null) {
                SwitchRow(stringResource(R.string.quick_share_receive), status.running) { on ->
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    if (on && !granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else setReceiving(on)
                }
                SettingsRow {
                    Text(statusText(status), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.quick_share_save_folder), style = MaterialTheme.typography.bodySmall)
                }
                lastResultText(status.last)?.let { SettingsNote(it) }
            }
            SettingsGroup(null) {
                NavRow(Icons.Rounded.Notifications, stringResource(R.string.quick_share_banner_title), stringResource(R.string.quick_share_banner_description)) {
                    context.startActivity(
                        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, QuickShareReceiverService.OFFER_CHANNEL_ID),
                    )
                }
            }
        }
    }
}

@Composable
private fun statusText(status: ReceiverStatus): String = when {
    !status.running -> stringResource(R.string.quick_share_status_off)
    status.progressPercent != null -> stringResource(R.string.quick_share_status_receiving, status.progressPercent)
    else -> stringResource(R.string.quick_share_status_listening, status.deviceName)
}

@Composable
private fun lastResultText(last: LastResult): String? = when (last) {
    LastResult.NONE -> null
    LastResult.DONE -> stringResource(R.string.quick_share_last_done)
    LastResult.REJECTED -> stringResource(R.string.quick_share_last_rejected)
    LastResult.CANCELLED -> stringResource(R.string.quick_share_last_cancelled)
    LastResult.FAILED -> stringResource(R.string.quick_share_last_failed)
}
