// Build file for the app itself: Android version targets, signing, and libraries.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.liquidglass.launcher"
    compileSdk = 35 // Android 15

    defaultConfig {
        applicationId = "com.liquidglass.launcher"
        minSdk = 31        // Android 12 and up (needed for real-time blur later)
        targetSdk = 35     // Android 15, as on the Nothing Phone 3a
        versionCode = 1
        versionName = "0.1"
    }

    // A fixed signing key lives in the repo so every build from GitHub
    // carries the same signature — that way updates install cleanly over
    // the previous version instead of demanding an uninstall.
    signingConfigs {
        getByName("debug") {
            storeFile = rootProject.file("signing/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
}
