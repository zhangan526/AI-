import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.desktoppet"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.desktoppet"
        minSdk = 26
        targetSdk = 36
        versionCode = 11
        versionName = "1.8.0"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(project(":reef"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}

configurations.all {
    resolutionStrategy {
        force("androidx.compose.material3:material3:1.5.0-alpha20")
    }
}
