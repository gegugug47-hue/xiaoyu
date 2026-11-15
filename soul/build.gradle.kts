plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

// M5-08：:soul 覆盖率（目标 ≥80%），报告输出到 build/reports/jacoco/test/html
// Kotlin JVM 插件已自带 jacocoTestReport 任务，这里只做配置，避免重复注册。
tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(
        fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
            exclude(
                "**/R.class",
                "**/BuildConfig.*",
                "**/*\$Companion.class",
            )
        },
    )
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(
        fileTree(layout.buildDirectory) { include("jacoco/test.exec", "jacoco/testDebugUnitTest.exec") },
    )
}

tasks.named("check") { dependsOn("jacocoTestReport") }
