plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.rohit.videoprocessor"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.rohit.videoprocessor"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        // Generates BuildConfig.DEBUG, used to gate the accuracy-debugging screens
        // (DebugScreen/DebugSettingsScreen) out of release builds entirely.
        buildConfig = true
    }

    // The bundled .tflite model must stay uncompressed in the APK so it can be
    // memory-mapped directly (FileChannel.map) instead of copied into a heap buffer.
    androidResources {
        noCompress += "tflite"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mlkit.face.detection)
    // Pinned to the last release published as org.tensorflow:tensorflow-lite before the
    // artifact was renamed/relocated to com.google.ai.edge.litert - that newer package
    // pulls in unrelated dynamic model-download (AiPack) permissions/services this
    // fully-offline app has no use for. The Java API (org.tensorflow.lite.Interpreter)
    // is unaffected either way.
    implementation(libs.tensorflow.lite)

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
