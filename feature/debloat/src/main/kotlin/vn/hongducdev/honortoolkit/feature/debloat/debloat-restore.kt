package vn.hongducdev.honortoolkit.feature.debloat

internal data class RestoreResult(val restored: Int, val failedPackage: String? = null, val output: String = "")

/** Stops at the first package that fails so a bad state is not compounded across the rest. */
internal suspend fun restorePackages(packages: List<String>, enable: suspend (String) -> DebloatOutcome): RestoreResult {
    var restored = 0
    for (packageName in packages) {
        when (val outcome = enable(packageName)) {
            DebloatOutcome.Done -> restored++
            is DebloatOutcome.Failed -> return RestoreResult(restored, packageName, outcome.output)
            is DebloatOutcome.Blocked, DebloatOutcome.InvalidName -> return RestoreResult(restored, packageName)
        }
    }
    return RestoreResult(restored)
}
