plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "vn.hongducdev.honortoolkit.feature.automation"
    compileSdk = 36

    defaultConfig {
        minSdk = 30
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:adb"))
    implementation(project(":core:ui"))
    // The "Toolkit feature" action writes the same catalogue keys the tune page uses.
    implementation(project(":feature:tune"))
    // The "Circle to Search" action runs the same trigger as the strip and the tile.
    implementation(project(":feature:circle-search"))
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub on the JVM, so the unit tests use the real implementation.
    testImplementation("org.json:json:20240303")
}
