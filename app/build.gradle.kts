plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.anthony.skywidget"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.anthony.skywidget"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Sign release builds with the debug key so `assembleRelease` works
            // without requiring a keystore in CI. For Play Store distribution,
            // replace this with a proper signing config.
            signingConfig = signingConfigs.getByName("debug")
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // WorkManager — drives the 15-minute refresh cycle.
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Fused location — battery-friendly GPS/network location.
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // Coroutines — for async Open-Meteo fetch inside the worker.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // JSON — kept simple with org.json from the Android framework; no extra dep needed.
}
