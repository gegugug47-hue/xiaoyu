import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.java.TargetJvmEnvironment

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}

/**
 * M0-02 模块依赖守卫（执行书 2.1 依赖规则）。
 *
 * 只检查各模块声明的依赖（不触发网络解析），规则：
 * - `:soul` / `:core-model` 是纯 Kotlin(JVM) 模块，禁止任何 `android.*` / `androidx.*` / `com.android.*`，
 *   以及 Room / OkHttp / Retrofit / Compose 等 Android 生态库；
 * - `:data` / `:brain` / `:service` 禁止 Compose；
 * - 模块间依赖只允许执行书表格中声明的方向（`allowedProjects`）。
 */
data class ModuleDepRule(
    val forbiddenGroupPrefixes: List<String> = emptyList(),
    val forbiddenModuleKeywords: List<String> = emptyList(),
    val allowedProjects: Set<String> = emptySet(),
)

val moduleDepRules: Map<String, ModuleDepRule> = mapOf(
    ":core-model" to ModuleDepRule(
        forbiddenGroupPrefixes = listOf("android.", "androidx.", "com.android."),
        forbiddenModuleKeywords = listOf("compose", "room", "okhttp", "retrofit", "work-runtime"),
    ),
    ":soul" to ModuleDepRule(
        forbiddenGroupPrefixes = listOf("android.", "androidx.", "com.android."),
        forbiddenModuleKeywords = listOf("compose", "room", "okhttp", "retrofit", "work-runtime"),
        allowedProjects = setOf(":core-model"),
    ),
    ":brain" to ModuleDepRule(
        forbiddenModuleKeywords = listOf("compose", "room"),
        allowedProjects = setOf(":core-model", ":soul", ":data"),
    ),
    ":data" to ModuleDepRule(
        forbiddenModuleKeywords = listOf("compose"),
        allowedProjects = setOf(":core-model", ":soul"),
    ),
    ":service" to ModuleDepRule(
        forbiddenModuleKeywords = listOf("compose"),
        allowedProjects = setOf(":core-model", ":soul", ":data", ":brain"),
    ),
)

tasks.register("assertModuleDeps") {
    group = "verification"
    description = "断言模块依赖方向不被破坏（:soul 禁 android/androidx，:data/:brain/:service 禁 Compose）"
    doLast {
        val violations = mutableListOf<String>()
        moduleDepRules.forEach { (path, rule) ->
            val module = findProject(path) ?: run {
                violations += "模块 $path 不存在（settings.gradle.kts 未 include）"
                return@forEach
            }
            val seen = mutableSetOf<String>()
            module.configurations.forEach { configuration ->
                configuration.dependencies.forEach depLoop@{ dependency ->
                    val projectDep = dependency as? org.gradle.api.artifacts.ProjectDependency
                    if (projectDep != null) {
                        val target = projectDep.path
                        val key = "project:$target"
                        if (seen.add(key) && target !in rule.allowedProjects && target != path) {
                            violations += "$path 非法依赖 $target（仅允许 ${rule.allowedProjects.ifEmpty { "无模块依赖" }}）"
                        }
                        return@depLoop
                    }
                    val group = dependency.group.orEmpty()
                    val name = dependency.name
                    val key = "$group:$name"
                    if (!seen.add(key)) return@depLoop
                    val forbiddenPrefix = rule.forbiddenGroupPrefixes.firstOrNull { group.startsWith(it) }
                    if (forbiddenPrefix != null) {
                        violations += "$path 非法依赖 $key（禁止组前缀 $forbiddenPrefix）"
                        return@depLoop
                    }
                    val forbiddenKeyword = rule.forbiddenModuleKeywords.firstOrNull {
                        name.contains(it, ignoreCase = true)
                    }
                    if (forbiddenKeyword != null) {
                        violations += "$path 非法依赖 $key（禁止关键字 $forbiddenKeyword）"
                    }
                }
            }
        }
        if (violations.isNotEmpty()) {
            throw GradleException(
                "模块依赖守卫失败（执行书 2.1）:\n" + violations.distinct().joinToString("\n") { " - $it" },
            )
        }
        logger.lifecycle("assertModuleDeps: 模块边界检查通过（${moduleDepRules.keys.joinToString()}）")
    }
}

// ---- M0-03 静态检查基线 ----
// 采用 CLI（JavaExec）而不是 Gradle 插件，避免与 AGP 9 / Gradle 9 的插件兼容性问题。
// 首次接入时历史代码存在存量风格问题，默认不阻断构建（ignoreExitValue），
// 加 `-PstrictStaticCheck` 后按严格模式失败，CI 可逐步开启。
val ktlintCli: Configuration = configurations.create("ktlintCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        // ktlint-cli 的 Gradle 元数据同时提供普通 jar 与 shadow jar 两个变体，
        // 显式声明 external bundling 选普通（runtimeElements）变体，避免变体歧义。
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.STANDARD_JVM),
        )
    }
}
val detektCli: Configuration = configurations.create("detektCli") {
    isCanBeConsumed = false
    isCanBeResolved = true
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.JAR))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
    }
}

dependencies {
    add(ktlintCli.name, "com.pinterest.ktlint:ktlint-cli:1.8.0")
    add(detektCli.name, "io.gitlab.arturbosch.detekt:detekt-cli:1.23.8")
}

val strictStaticCheck: Boolean = providers.gradleProperty("strictStaticCheck").isPresent
val sourceRoots = listOf("app", "core-model", "soul", "data", "brain", "service")

tasks.register<JavaExec>("ktlintCheck") {
    group = "verification"
    description = "ktlint 代码风格检查（M0-03）"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args = sourceRoots.map { "$it/src/**/*.kt" }
    isIgnoreExitValue = !strictStaticCheck
}

tasks.register<JavaExec>("ktlintFormat") {
    group = "verification"
    description = "ktlint 自动修复代码风格（M0-03）"
    classpath = ktlintCli
    mainClass.set("com.pinterest.ktlint.Main")
    args = listOf("-F") + sourceRoots.map { "$it/src/**/*.kt" }
    isIgnoreExitValue = true
}

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "detekt 静态检查（M0-03：MagicNumber 白名单 0/1/-1/100、ComplexMethod）"
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    args = listOf(
        "--input",
        sourceRoots.joinToString(",") { "$it/src" },
        "--config",
        layout.projectDirectory.file("detekt.yml").asFile.absolutePath,
        "--build-upon-default-config",
        "--exclude",
        "**/build/**,**/resources/**",
        "--report",
        "xml:${layout.buildDirectory.get().asFile.absolutePath}/reports/detekt.xml",
    )
    isIgnoreExitValue = !strictStaticCheck
}
