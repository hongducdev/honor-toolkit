package vn.hongducdev.honortoolkit.feature.debloat

enum class ProtectionReason { CoreSystem, InUse }

/** Packages the UI can never disable, regardless of what the user selects. */
internal object DebloatDenylist {
    private val exactPackages = setOf(
        "android",
        "com.android.shell",
        "com.android.systemui",
        "com.android.settings",
        "com.android.phone",
        "com.android.server.telecom",
        "com.android.bluetooth",
        "com.android.nfc",
        "com.android.packageinstaller",
        "com.google.android.packageinstaller",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller",
        "com.android.webview",
        "com.google.android.webview",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.android.vending",
        "com.hihonor.android.launcher",
        "com.huawei.android.launcher",
        "androidhnext",
        "com.hihonor.systemserver",
        "com.hihonor.systemmanager",
        "com.hihonor.id",
        "com.android.documentsui",
        "com.android.externalstorage",
        "com.android.ext.services",
        "com.google.android.ext.services",
        "com.google.android.providers.media.module",
        "com.google.android.trichromelibrary",
        "com.android.se",
        "com.android.ims",
        "com.android.carrierconfig",
        "com.android.location.fused",
    )

    private val packagePrefixes = listOf(
        "com.android.providers.",
        "com.android.networkstack",
        "com.android.internal.",
        "com.android.wifi",
        "com.android.frameworkres",
        "com.qualcomm.qti.",
        "com.qti.",
    )

    fun protectionOf(packageName: String, inUse: Set<String>): ProtectionReason? = when {
        packageName in exactPackages || packagePrefixes.any(packageName::startsWith) -> ProtectionReason.CoreSystem
        packageName in inUse -> ProtectionReason.InUse
        else -> null
    }
}
