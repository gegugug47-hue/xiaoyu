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

// M5-08：:soul 覆盖率门禁（行覆盖 ≥80%），报告输出到 build/reports/jacoco/test/html
// Kotlin JVM 插件已自带 jacocoTestReport / jacocoTestCoverageVerification 任务，这里只做配置。
val jacocoClassDirs = fileTree(layout.buildDirectory.dir("classes/kotlin/main")) {
    exclude(
        "**/R.class",
        "**/BuildConfig.*",
        "**/*\$Companion.class",
    )
}
val jacocoExecData = fileTree(layout.buildDirectory) {
    include("jacoco/test.exec", "jacoco/testDebugUnitTest.exec")
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
    classDirectories.setFrom(jacocoClassDirs)
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(jacocoExecData)
}

tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(jacocoClassDirs)
    sourceDirectories.setFrom(files("src/main/kotlin"))
    executionData.setFrom(jacocoExecData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

// check 会连带跑覆盖率校验，低于阈值直接失败
tasks.named("check") { dependsOn("jacocoTestReport", "jacocoTestCoverageVerification") }
