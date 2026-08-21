plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.photoglobe.spike"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.photoglobe.spike"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1-spike"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

// Three dependencies, all already in the local Gradle cache.
// No Compose, no Material libraries, no network client - see README.
dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-ktx:1.8.0")
    implementation("androidx.exifinterface:exifinterface:1.4.1")
}
