import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

val appVersionCode = (findProperty("honorToolkit.versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (findProperty("honorToolkit.versionName") as String?) ?: "0.1.0"

android {
    namespace = "vn.hongducdev.honortoolkit"
    compileSdk = 36

    defaultConfig {
        applicationId = "vn.hongducdev.honortoolkit"
        minSdk = 30
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
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
    implementation(project(":feature:circle-search"))
    implementation(project(":feature:debloat"))
    implementation(project(":feature:notification-fix"))
    implementation(project(":feature:gms-check"))
    implementation(project(":feature:permissions"))
    implementation(project(":feature:quickshare"))
    implementation(project(":feature:tune"))
    implementation(project(":feature:update"))
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3:1.5.0-alpha18")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    testImplementation("junit:junit:4.13.2")
}
