// build.gradle.kts (module)

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.insail.nmeagpsserver"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.insail.nmeagpsserver"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.000"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("D:/AndroidStudioProjects/KeyStore/nmeagpsserver-jks.jks")
            storePassword = System.getenv("KEYSTORE_PWD")
            keyAlias = "nmea"
            keyPassword = System.getenv("KEY_PWD")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false // ou true si tu configures ProGuard
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }
}

// ✅ Remplace l’ancien kotlinOptions par compilerOptions
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
        // si besoin, tu peux ajouter d’autres options ici:
        // freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.usb.serial)
    implementation(libs.androidx.preference.ktx)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
