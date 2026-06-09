import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

android {
    namespace = "com.iosync.watchface"
    compileSdk = 36

    if (keystorePropertiesFile.exists()) {
        signingConfigs {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    defaultConfig {
        applicationId = "com.iosync.app"
        minSdk = 30  // Wear OS 3+ (API 30)
        targetSdk = 36
        versionCode = 444
        versionName = "4.4.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // OpenWeatherMap API Key aus local.properties oder Umgebungsvariable
        // (ab v5 ruft die Uhr das Wetter direkt selbst ab)
        val localProps = rootProject.file("local.properties")
        val owmKey = if (localProps.exists()) {
            val props = Properties()
            props.load(localProps.inputStream())
            props.getProperty("OPENWEATHER_API_KEY", "")
        } else {
            ""
        }
        val apiKey = System.getenv("OPENWEATHER_API_KEY") ?: owmKey
        buildConfigField("String", "OPENWEATHER_API_KEY", "\"$apiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        buildConfig = true
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
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.wear.compose.material)
    implementation(libs.androidx.wear.compose.foundation)
    debugImplementation(libs.androidx.wear.compose.ui.tooling)

    // Moshi
    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Health Services (Passive Monitoring für HR, Steps)
    implementation(libs.health.services.client)
    implementation("com.google.guava:guava:33.0.0-android")

    // Health Connect (direktes Lesen von Kalorien, SpO2, Schlaf aus der Watch-DB)
    implementation(libs.health.connect.client)

    // Wearable Data Layer
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // HTTP/SSE — ab v5 fragt die Uhr ioBroker-Datenpunkte direkt selbst ab
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.sse)

    // Test
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
}
