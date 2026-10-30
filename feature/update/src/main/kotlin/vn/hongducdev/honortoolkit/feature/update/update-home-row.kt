package vn.hongducdev.honortoolkit.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import vn.hongducdev.honortoolkit.core.ui.NavRow

/** Home row into the update page. Shows the installed version, or the waiting release if there is one. */
@Composable
fun UpdateNavRow(currentVersion: String, availableVersion: String?, onClick: () -> Unit) {
    NavRow(
        icon = Icons.Rounded.SystemUpdate,
        title = stringResource(R.string.update_nav_title),
        description = if (availableVersion != null) {
            stringResource(R.string.update_nav_available, availableVersion)
        } else {
            stringResource(R.string.update_nav_current, currentVersion)
        },
        onClick = onClick,
    )
}

/** Prominent card above the Home tools when a newer release is waiting. */
@Composable
fun UpdateAvailableCard(versionName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(scheme.primaryContainer)
            .clickable(role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onClick()
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(scheme.primary, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.SystemUpdate, contentDescription = null, tint = scheme.onPrimary)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                stringResource(R.string.update_banner_title, versionName),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.update_banner_description),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onPrimaryContainer,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = scheme.onPrimaryContainer)
    }
}
