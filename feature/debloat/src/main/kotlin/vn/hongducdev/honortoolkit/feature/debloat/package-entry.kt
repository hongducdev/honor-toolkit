package vn.hongducdev.honortoolkit.feature.debloat

data class PackageEntry(
    val packageName: String,
    val label: String,
    val enabled: Boolean,
    val protection: ProtectionReason?,
    val verified: Boolean,
    val suggestion: PackageSuggestion? = null,
)
