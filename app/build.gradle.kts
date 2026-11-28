import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val keystore = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { f ->
    Properties().apply { f.inputStream().use { load(it) } }
}

/* 更新索引地址：gradle.properties 里配，留空表示不启用（App 不会发起任何请求）。
   放构建期而不是写死在代码里，是为了换 GitHub / Cloudflare 托管时只改一行配置。 */
val updateIndexUrl = (project.findProperty("tvhub.updateIndexUrl") as String?)?.trim().orEmpty()

android {
    namespace = "app.tvhub"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.tvhub"
        minSdk = 28
        targetSdk = 36
        /* 版本号：versionName 按 semver 走（首个公开版本 1.0.0），versionCode 每次发版 +1。
           versionCode 只增不减：真机装过上游 0.6.0（vc10），比它小的包无法覆盖安装，
           所以起点是 11，而不是从公式（major*10000+…）推出来的数。 */
        versionCode = 15
        versionName = "1.0.4"
        buildConfigField("String", "UPDATE_INDEX_URL", "\"$updateIndexUrl\"")
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // Crossfade 在主推位轮播里用；版本走 BOM，别依赖 tv-material 的传递依赖
    implementation("androidx.compose.animation:animation")
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
