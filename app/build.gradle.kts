plugins {
    id("com.android.application")
    kotlin("android")
}

android {
    namespace = "com.example.meuapp"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.meuapp"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
}
