import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// 发布签名（执行书 M6-05）：key.properties 与 keys/ 均不入库；CI 由 Secrets 还原出同样的文件
val keystoreProperties = Properties().apply {
    val file = rootProject.file("key.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null
// 仅 release 任务启用 ABI 拆分，避免影响日常 debug 安装
val releaseBuildRequested = gradle.startParameter.taskNames.any {
    it.contains("Release", ignoreCase = true) || it.contains("bundle", ignoreCase = true)
}

android {
    namespace = "com.xiaoyu.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.xiaoyu.app"
        minSdk = 26
        targetSdk = 36
        // 打 tag 发布时由 CI 传入 -PxiaoyuVersionName / -PxiaoyuVersionCode
        versionCode = (project.findProperty("xiaoyuVersionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("xiaoyuVersionName") as String?) ?: "1.0.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKey) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    // 分 ABI 出包（执行书 M6-04）：release 任务产出 app-<abi>-release.apk
    splits {
        abi {
            isEnable = releaseBuildRequested
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*")
        }
    }

    lint {
        warningsAsErrors = false
        abortOnError = false
        lintConfig = rootProject.file("lint.xml")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(project(":soul"))
    implementation(project(":core-model"))
    implementation(project(":data"))
    implementation(project(":brain"))
    implementation(project(":service"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    // M7-08 桌面小组件
    implementation(libs.androidx.glance.appwidget)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
