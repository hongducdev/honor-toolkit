package vn.hongducdev.honortoolkit.ui.home

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import androidx.annotation.DrawableRes
import java.text.SimpleDateFormat
import java.util.Locale
import vn.hongducdev.honortoolkit.R

data class DeviceInfo(
    val deviceName: String,
    val manufacturer: String,
    val model: String,
    val hardware: String,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String,
    val build: String,
    val totalStorage: Long,
    val usedStorage: Long,
    val totalRam: Long,
    val usedRam: Long,
)

fun readDeviceInfo(context: Context): DeviceInfo {
    val dataDir = StatFs(Environment.getDataDirectory().path)
    val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
    val deviceName = Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        ?: Settings.Secure.getString(context.contentResolver, "bluetooth_name")
        ?: Build.MODEL
    return DeviceInfo(
        deviceName = deviceName,
        manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
        model = Build.MODEL,
        hardware = Build.HARDWARE,
        androidVersion = Build.VERSION.RELEASE,
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH,
        build = Build.DISPLAY,
        totalStorage = dataDir.totalBytes,
        usedStorage = dataDir.totalBytes - dataDir.availableBytes,
        totalRam = memory.totalMem,
        usedRam = memory.totalMem - memory.availMem,
    )
}

@DrawableRes
fun androidLogoRes(sdkInt: Int): Int = when {
    sdkInt >= 37 -> R.drawable.android17
    sdkInt >= 36 -> R.drawable.android16
    sdkInt >= 35 -> R.drawable.android15
    sdkInt >= 34 -> R.drawable.android14
    sdkInt >= 33 -> R.drawable.android13
    sdkInt >= 31 -> R.drawable.android12
    else -> 0
}

fun androidDessertName(sdkInt: Int): String? = when (sdkInt) {
    37 -> "CinnamonBun"
    36 -> "Baklava"
    35 -> "Vanilla Ice Cream"
    34 -> "Upside Down Cake"
    33 -> "Tiramisu"
    32 -> "Snow Cone v2"
    31 -> "Snow Cone"
    30 -> "R"
    29 -> "Q"
    28 -> "Pie"
    else -> null
}

private val StandardSizesGb = listOf(1, 2, 3, 4, 6, 8, 10, 12, 16, 18, 24, 32, 64, 128, 256, 512, 1024, 2048)

/** Rounds up to the marketed size, since the OS reserves part of the real capacity (7.4 GiB of RAM reads as "8 GB"). */
fun formatHardwareSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "?"
    val rawGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
    val roundedGb = StandardSizesGb.firstOrNull { it >= rawGb * 0.9 } ?: Math.ceil(rawGb).toInt()
    return if (roundedGb >= 1024 && roundedGb % 1024 == 0) "${roundedGb / 1024} TB" else "$roundedGb GB"
}

fun formatSecurityPatch(patch: String): String {
    val date = runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(patch) }.getOrNull() ?: return patch
    return SimpleDateFormat("d MMMM, yyyy", Locale.getDefault()).format(date)
}
