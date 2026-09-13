import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun escapeBuildConfig(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

android {
    namespace = "com.example.desktoppet"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.geekathon.guardpet"
        minSdk = 26
        targetSdk = 35
        versionCode = 35
        versionName = "2.9.1-fidelity"
        val deepseekKey = localProps.getProperty("deepseek.api.key", "")
        val openaiKey = localProps.getProperty("openai.api.key", "")
        val openaiBase = localProps.getProperty("openai.base.url", "https://api.openai.com/v1")
        val dashscopeKey = localProps.getProperty("dashscope.api.key", "")
        buildConfigField("String", "DEEPSEEK_API_KEY", "\"${escapeBuildConfig(deepseekKey)}\"")
        buildConfigField("String", "OPENAI_API_KEY", "\"${escapeBuildConfig(openaiKey)}\"")
        buildConfigField("String", "OPENAI_BASE_URL", "\"${escapeBuildConfig(openaiBase)}\"")
        buildConfigField("String", "DASHSCOPE_API_KEY", "\"${escapeBuildConfig(dashscopeKey)}\"")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
