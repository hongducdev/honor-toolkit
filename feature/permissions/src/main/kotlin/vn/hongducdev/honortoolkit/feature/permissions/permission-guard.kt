package vn.hongducdev.honortoolkit.feature.permissions

enum class BlockReason { InvalidName, NotUserApp, SharedUid, FixedBySystem, NotChangeable }

private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")
private val OP_NAME = Regex("^[A-Z][A-Z0-9_]*$")

/** Names go straight into a shell command line, so anything unusual is rejected. */
internal fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)

internal fun isValidAppOpName(name: String): Boolean = OP_NAME.matches(name)

internal object PermissionGuard {
    /** Only third-party apps on user 0, and never when several apps share one UID. */
    fun checkApp(packageName: String, userPackages: Set<String>, sharedUid: Boolean): BlockReason? = when {
        !isValidPackageName(packageName) -> BlockReason.InvalidName
        packageName !in userPackages -> BlockReason.NotUserApp
        sharedUid -> BlockReason.SharedUid
        else -> null
    }

    /** [entry] is the permission as just read from the device; null means it is not a runtime permission of the app. */
    fun checkPermission(name: String, entry: PermissionEntry?): BlockReason? = when {
        !isValidPackageName(name) -> BlockReason.InvalidName
        entry == null -> BlockReason.NotChangeable
        entry.fixed -> BlockReason.FixedBySystem
        else -> null
    }

    fun checkAppOp(name: String): BlockReason? = if (isValidAppOpName(name)) null else BlockReason.InvalidName
}
