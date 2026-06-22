plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val defaultQtvRemoteConfigUrl = "https://raw.githubusercontent.com/Wq5881898/QTV/main/qtv.json"
val qtvRemoteConfigUrl = providers.gradleProperty("QTV_REMOTE_CONFIG_URL").orElse(defaultQtvRemoteConfigUrl)
val defaultQtvUpdateUrl = "https://api.github.com/repos/Wq5881898/QTV/releases/latest"
val qtvUpdateUrl = providers.gradleProperty("QTV_UPDATE_URL").orElse(defaultQtvUpdateUrl)

android {
    namespace = "com.qtv.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.qtv.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.1.3"
        buildConfigField(
            "String",
            "QTV_REMOTE_CONFIG_URL",
            "\"${qtvRemoteConfigUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )
        buildConfigField(
            "String",
            "QTV_UPDATE_URL",
            "\"${qtvUpdateUrl.get().replace("\\", "\\\\").replace("\"", "\\\"")}\"",
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
