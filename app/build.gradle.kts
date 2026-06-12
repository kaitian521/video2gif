plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
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

    // 签名(参考 RenaAI-Android 模式):jks 在仓库根目录,debug/release 同一签名,
    // 便于覆盖安装与一键出包。
    signingConfigs {
        create("release") {
            keyAlias = "video2gif_alias"
            keyPassword = "video2gif_release"
            storeFile = File(rootDir, "video2gif.jks")
            storePassword = "video2gif_release"
        }
        getByName("debug") {
            keyAlias = "video2gif_alias"
            keyPassword = "video2gif_release"
            storeFile = File(rootDir, "video2gif.jks")
            storePassword = "video2gif_release"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
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

    androidResources {
        // 20 种支持语言(资源目录限定符,Indonesian 为旧码 "in"):
        // 显式声明支持集,并把依赖库携带的其它 locale 资源裁掉。
        localeFilters += listOf(
            "en", "zh", "hi", "es", "fr", "ar", "bn", "pt", "ru", "ur",
            "in", "de", "ja", "sw", "mr", "te", "tr", "ta", "vi", "ko",
        )
    }

    bundle {
        language {
            // AAB 不按语言拆分:20 种语言全部打进 base APK,
            // 侧载/无 Play 分发场景语言包也完整(用户要求保证打入 apk/aab)。
            enableSplit = false
        }
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
    // Creation 作品页:导出记录本地存储
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
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
    // Google Play In-App Review 评分弹窗(参考 ComplexMusic)
    implementation(libs.play.app.review.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}