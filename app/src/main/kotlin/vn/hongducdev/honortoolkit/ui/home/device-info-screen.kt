package vn.hongducdev.honortoolkit.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vn.hongducdev.honortoolkit.R
import vn.hongducdev.honortoolkit.core.ui.LocalToolbarClearance
import vn.hongducdev.honortoolkit.core.ui.ProvideToolbar
import vn.hongducdev.honortoolkit.core.ui.ToolbarSpec

private const val MaxStretchPx = 350f
private const val StretchResistance = 0.5f
private val CardCorner = 24.dp
private val CardGap = 2.dp
private val LogoSize = 200.dp

@Composable
fun DeviceInfoScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val info = remember { readDeviceInfo(context) }
    val scope = rememberCoroutineScope()
    val stretch = remember { Animatable(0f) }
    val scroll = rememberScrollState()

    ProvideToolbar(
        ToolbarSpec.Page(
            title = stringResource(R.string.device_info_title),
            backDescription = stringResource(R.string.back),
            onBack = onBack,
        ),
    )

    // Pulling past the top stretches the hero logo; releasing springs it back.
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y >= 0f || stretch.value <= 0f) return Offset.Zero
                val consumed = maxOf(available.y, -stretch.value)
                scope.launch { stretch.snapTo(stretch.value + consumed) }
                return Offset(0f, consumed)
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y <= 0f || scroll.value != 0 || source != NestedScrollSource.UserInput) return Offset.Zero
                val next = (stretch.value + available.y * StretchResistance).coerceAtMost(MaxStretchPx)
                scope.launch { stretch.snapTo(next) }
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                stretch.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                return Velocity.Zero
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .nestedScroll(connection)
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = LocalToolbarClearance.current),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DeviceHero(info = info, stretch = { stretch.value })
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(CardCorner)),
            verticalArrangement = Arrangement.spacedBy(CardGap),
        ) {
            VersionCard(info)
            HardwareCard(info)
            BatteryCard()
        }
    }
}

@Composable
private fun DeviceHero(info: DeviceInfo, stretch: () -> Float) {
    val logo = androidLogoRes(info.sdkInt)
    val extra = with(LocalDensity.current) { stretch().toDp() }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (logo != 0) {
            Icon(
                painter = painterResource(logo),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .graphicsLayer {
                        val scale = 1f + stretch() / 1000f
                        scaleX = scale
                        scaleY = scale
                    }
                    .size(LogoSize + extra)
                    .padding(top = 12.dp, bottom = 32.dp),
            )
        }
        Text(
            info.deviceName,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        Text(
            "${info.manufacturer} ${info.model} (${info.hardware})",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoCard(vertical: androidx.compose.ui.unit.Dp = 16.dp, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceBright, MaterialTheme.shapes.extraSmall)
            .padding(horizontal = 16.dp, vertical = vertical),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
private fun VersionCard(info: DeviceInfo) {
    val scheme = MaterialTheme.colorScheme
    val dessert = androidDessertName(info.sdkInt)
    InfoCard {
        Text(
            buildString {
                append("Android ").append(info.androidVersion)
                if (dessert != null) append(" (").append(dessert).append(')')
            },
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
        Text(
            stringResource(R.string.device_info_api_patch, info.sdkInt, formatSecurityPatch(info.securityPatch)),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.device_info_build_line, info.build),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun HardwareCard(info: DeviceInfo) {
    InfoCard {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            HardwareStat(Icons.Rounded.Dns, stringResource(R.string.device_info_storage), formatHardwareSize(info.totalStorage))
            HardwareStat(Icons.Rounded.Memory, stringResource(R.string.device_info_ram), formatHardwareSize(info.totalRam))
        }
    }
}

@Composable
private fun HardwareStat(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun BatteryCard() {
    val context = LocalContext.current
    var level by remember { mutableIntStateOf(readBatteryLevel(context)) }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                level = batteryPercent(intent)
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { context.unregisterReceiver(receiver) }
    }
    InfoCard(vertical = 20.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.BatteryFull, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Text("$level%", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(
                stringResource(R.string.device_info_battery),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun readBatteryLevel(context: Context): Int =
    context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let(::batteryPercent) ?: 0

private fun batteryPercent(intent: Intent): Int {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) level * 100 / scale else 0
}
