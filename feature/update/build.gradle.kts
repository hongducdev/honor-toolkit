plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

val githubRepo = (rootProject.findProperty("honorToolkit.githubRepo") as String?) ?: "hongducdev/honor-toolkit"

android {
    namespace = "vn.hongducdev.honortoolkit.feature.update"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
