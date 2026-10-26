pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
    }
}

rootProject.name = "honor-toolkit"
include(":app")
include(":core:adb")
include(":core:quickshare")
include(":feature:quickshare")
include(":core:ui")
include(":feature:circle-search")
include(":feature:debloat")
include(":feature:notification-fix")
include(":feature:permissions")
