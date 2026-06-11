plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dodotechhk.video2gif"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.dodotechhk.video2gif"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        // 全局 @OptIn(UnstableApi::class):多数 Media3 编辑 API 带 @UnstableApi(§10.4)
        optIn.add("androidx.media3.common.util.UnstableApi")
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // Media3 全家桶:统一锁同一具体版本(见 gradle/libs.versions.toml 的 media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.ui)
    implementation(libs.kotlinx.coroutines.android)
    // P9 GIF/WebP 转码:ffmpeg-kit 社区 fork(16KB 对齐,LGPLv3;带 .so,见技术方案 §5.2/§8)
    implementation(libs.ffmpeg.kit)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}