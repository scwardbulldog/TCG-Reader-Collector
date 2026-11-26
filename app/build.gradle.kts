import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "tcg.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "tcg.app"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read-only token for the private Catalog repo: from local.properties (catalog.token) or the
        // CATALOG_TOKEN environment variable. Neither is committed; it is the app's only credential.
        val localProperties = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { load(it) }
        }
        val catalogToken = localProperties.getProperty("catalog.token") ?: System.getenv("CATALOG_TOKEN") ?: ""
        buildConfigField("String", "CATALOG_TOKEN", "\"$catalogToken\"")
        buildConfigField("String", "CATALOG_REPO", "\"scwardbulldog/TCG-Catalog\"")
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

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets["androidTest"].assets.srcDir("$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":domain"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // CameraX for the live preview and frame analysis; see tcg.app.scanning.
    val cameraX = "1.4.1"
    implementation("androidx.camera:camera-core:$cameraX")
    implementation("androidx.camera:camera-camera2:$cameraX")
    implementation("androidx.camera:camera-lifecycle:$cameraX")
    implementation("androidx.camera:camera-view:$cameraX")
    // Bundled Latin model: runs on-device with no Play Store services.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    val room = "2.6.1"
    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    androidTestImplementation("androidx.room:room-testing:$room")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation(kotlin("test"))
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
}
