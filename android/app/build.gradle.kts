plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.fitnessapp.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.fitnessapp.android"
        minSdk = 28
        targetSdk = 37
        versionCode = 5
        versionName = "0.4.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "DEFAULT_BASE_URL", "\"https://api.hamghadam.ba4b0d.ir/api/v1\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"590964300109-p10jff24glu9mite50u27ho56jl79hml.apps.googleusercontent.com\"")
        }
        debug {
            versionNameSuffix = "-debug"
            buildConfigField("String", "DEFAULT_BASE_URL", "\"http://10.0.2.2:8000/api/v1\"")
            buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"590964300109-p10jff24glu9mite50u27ho56jl79hml.apps.googleusercontent.com\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Health Connect (Jetpack SDK) — read-only usage
    implementation(libs.androidx.health.connect.client)

    // Background sync
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase BOM & FCM push
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    // health-connect-client 1.2.0-alpha05 pulls fragment:1.1.0, which breaks
    // registerForActivityResult (lint InvalidFragmentVersionForActivityResult);
    // pin a modern fragment so Compose + permission launchers are safe.
    implementation(libs.androidx.fragment.ktx)

    // Networking
    implementation(libs.okhttp)

    // Google Sign-In & Credential Manager
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.play.services.auth)

    // Image loading
    implementation(libs.coil.compose)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
