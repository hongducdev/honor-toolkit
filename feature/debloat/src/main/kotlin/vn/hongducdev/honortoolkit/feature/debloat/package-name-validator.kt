package vn.hongducdev.honortoolkit.feature.debloat

private val PACKAGE_NAME = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*$")

/** Package names go straight into a shell command line, so anything unusual is rejected. */
internal fun isValidPackageName(name: String): Boolean = PACKAGE_NAME.matches(name)
