plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.karoo.aqi"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.karoo.aqi"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }
    
    // Notice: The entire buildFeatures { compose = true } block is gone.
}

dependencies {
    // The core Karoo SDK MUST be implementation
    implementation("io.hammerhead:karoo-ext:1.1.8")
    
    // Core Android basics
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}