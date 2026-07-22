// Build file for the local music player app.
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.localmusic.player"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localmusic.player"
        minSdk = 31
        targetSdk = 35
        versionCode = 2
        versionName = "0.2"
    }

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
    // Palette pulls the dominant colors out of album art for the
    // color-bleed background, like Apple Music / Spotify now-playing.
    implementation("androidx.palette:palette-ktx:1.0.0")
    // Media3 / ExoPlayer: the robust playback engine (MP3, AAC, FLAC,
    // OGG, Opus, WAV, MKV audio and more), plus MediaSession for
    // lock-screen and notification controls and background playback.
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    implementation("androidx.media3:media3-ui:1.4.1")
}
