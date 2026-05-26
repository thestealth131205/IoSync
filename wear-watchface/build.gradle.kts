plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.iosync.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.iosync.watchface"
        minSdk = 30  // Wear OS 3+ (API 30)
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)

    // Jetpack Watch Face API
    implementation(libs.androidx.watchface)
    implementation(libs.androidx.watchface.client)
    implementation(libs.androidx.watchface.style)
    implementation(libs.androidx.watchface.editor)

    // Complications
    implementation(libs.androidx.watchface.complications.data)
    implementation(libs.androidx.watchface.complications.rendering)

    // Compose for Wear OS (used for WatchFaceEditor activity)
    implementation(platform(libs.androidx.wear.compose.bom))
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    // Moshi
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
