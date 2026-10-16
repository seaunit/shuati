import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// 服务端地址：默认线上域名。本地联调时覆盖，例如
//   .\gradlew.bat :app:assembleDebug -PshuatiApiBaseUrl=http://192.168.1.5:8080
val apiBaseUrl = (project.findProperty("shuatiApiBaseUrl") as String?) ?: "https://www.seaunit.site"

android {
    namespace = "site.seaunit.shuati"
    compileSdk = 36

    defaultConfig {
        applicationId = "site.seaunit.shuati"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "BASE_URL", "\"$apiBaseUrl\"")
        // 线上是 HTTPS；明文 HTTP 只放给 debug 包，供本机联调
        manifestPlaceholders["usesCleartextTraffic"] = "false"
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
}
