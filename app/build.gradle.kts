plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mcandle.bleapp"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mcandle.bleapp"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        // BLE 광고 시 필수: Android 12+에서 필요
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // Fragment + ViewModel
    implementation("androidx.fragment:fragment-ktx:1.7.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.0")

    // BLE + Permissions (Android 12+)
    implementation("androidx.activity:activity-ktx:1.9.0")

    // Optional: Logging
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    
    // Splash Screen API (Android 12+ 호환)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // For local unit tests
    testImplementation(libs.junit)

    // For Android Instrumented Tests
    androidTestImplementation(libs.androidx.junit) // Uses androidx.test.ext:junit from your toml
    androidTestImplementation(libs.androidx.espresso.core) // For Espresso, if you use it
}


