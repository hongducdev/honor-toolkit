package vn.hongducdev.honortoolkit.ui.home

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarAction
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec
import vn.hongducdev.honortoolkit.core.ui.ToolkitLoadingIndicator

private enum class UsageSort { Ram, Storage }

private val GroupCorner = 24.dp
private val IconSize = 40.dp
private const val IconPx = 96

@Composable
fun DeviceUsageScreen(onBack: () -> Unit, modifier: Modifier = Modifier, viewModel: DeviceUsageViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(UsageSort.Ram) }
    val context = LocalContext.current
    val device = remember { readDeviceInfo(context) }

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.device_usage_title),
            backDescription = stringResource(R.string.back),
            onBack = onBack,
            fab = ToolbarAction(Icons.Rounded.Refresh, stringResource(R.string.device_usage_refresh), viewModel::refresh),
        ),
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .statusBarsPadding()
            .padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SortToggle(sort, onSelect = { sort = it }, modifier = Modifier.padding(horizontal = 16.dp))
        when (val load = state) {
            null -> Box(Modifier.fillMaxSize().padding(top = 96.dp), contentAlignment = Alignment.TopCenter) { ToolkitLoadingIndicator() }
            is UsageLoad.Failed -> FailureNote(load.message, modifier = Modifier.padding(horizontal = 16.dp))
            is UsageLoad.Loaded -> UsageList(load.apps, sort, device)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortToggle(sort: UsageSort, onSelect: (UsageSort) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleOption(sort == UsageSort.Ram, Icons.Rounded.Memory, R.string.device_info_ram, { onSelect(UsageSort.Ram) },
            ButtonGroupDefaults.connectedLeadingButtonShapes(), Modifier.weight(1f))
        ToggleOption(sort == UsageSort.Storage, Icons.Rounded.Dns, R.string.device_info_storage, { onSelect(UsageSort.Storage) },
            ButtonGroupDefaults.connectedTrailingButtonShapes(), Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ToggleOption(
    checked: Boolean,
    icon: ImageVector,
    label: Int,
    onClick: () -> Unit,
    shapes: androidx.compose.material3.ToggleButtonShapes,
    modifier: Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        shapes = shapes,
        modifier = modifier.semantics { role = Role.RadioButton },
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun UsageList(apps: List<AppUsage>, sort: UsageSort, device: DeviceInfo) {
    val context = LocalContext.current
    val sorted = remember(apps, sort) {
        when (sort) {
            UsageSort.Ram -> apps.filter { it.ramBytes > 0 }.sortedByDescending { it.ramBytes }
            UsageSort.Storage -> apps.filter { it.storageBytes > 0 }.sortedByDescending { it.storageBytes }
        }
    }
    val top = remember(sorted) { sorted.firstOrNull()?.let { if (sort == UsageSort.Ram) it.ramBytes else it.storageBytes } ?: 1L }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = LocalToolbarClearance.current),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        item(key = "summary") {
            Box(Modifier.padding(bottom = 14.dp)) { Summary(sort, device, sorted.size, context) }
        }
        itemsIndexed(sorted, key = { _, app -> app.packageName }) { index, app ->
            val corner = 4.dp
            val shape = RoundedCornerShape(
                topStart = if (index == 0) GroupCorner else corner,
                topEnd = if (index == 0) GroupCorner else corner,
                bottomStart = if (index == sorted.lastIndex) GroupCorner else corner,
                bottomEnd = if (index == sorted.lastIndex) GroupCorner else corner,
            )
            UsageRow(app, sort, share = (if (sort == UsageSort.Ram) app.ramBytes else app.storageBytes).toFloat() / top, shape, context)
        }
    }
}

@Composable
private fun Summary(sort: UsageSort, device: DeviceInfo, appCount: Int, context: Context) {
    val scheme = MaterialTheme.colorScheme
    val used = if (sort == UsageSort.Ram) device.usedRam else device.usedStorage
    val total = if (sort == UsageSort.Ram) device.totalRam else device.totalStorage
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner + 4.dp))
            .background(scheme.primaryContainer)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                Formatter.formatShortFileSize(context, used),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimaryContainer,
            )
            Text(
                stringResource(R.string.device_usage_of_total, formatHardwareSize(total)),
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        LinearProgressIndicator(
            progress = { (used.toFloat() / total).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = scheme.primary,
            trackColor = scheme.onPrimaryContainer.copy(alpha = 0.15f),
        )
        Text(
            stringResource(R.string.device_usage_app_count, appCount),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun UsageRow(app: AppUsage, sort: UsageSort, share: Float, shape: RoundedCornerShape, context: Context) {
    val scheme = MaterialTheme.colorScheme
    val primaryBytes = if (sort == UsageSort.Ram) app.ramBytes else app.storageBytes
    val otherBytes = if (sort == UsageSort.Ram) app.storageBytes else app.ramBytes
    val otherLabel = stringResource(if (sort == UsageSort.Ram) R.string.device_info_storage else R.string.device_info_ram)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(scheme.surfaceBright)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(app.packageName)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(app.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { share.coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                trackColor = scheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
            if (otherBytes > 0) {
                Text(
                    "$otherLabel ${Formatter.formatShortFileSize(context, otherBytes)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            Formatter.formatShortFileSize(context, primaryBytes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun AppIcon(packageName: String) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(IconPx, IconPx).asImageBitmap() }.getOrNull()
        }
    }
    Box(
        modifier = Modifier.size(IconSize).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap, contentDescription = null, modifier = Modifier.size(IconSize))
        } else {
            Icon(Icons.Rounded.Android, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun FailureNote(message: String, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(GroupCorner))
            .background(scheme.errorContainer)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = scheme.onErrorContainer)
        Text(
            stringResource(R.string.device_usage_failed, message),
            style = MaterialTheme.typography.bodyLarge,
            color = scheme.onErrorContainer,
        )
    }
}
