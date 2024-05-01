plugins {
    alias(libs.plugins.gradle.plugin.publish)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.sam.receiver)
    alias(libs.plugins.build.config)
}

val pluginDir = "fluxo-kmp-conf"
val pluginId = libs.plugins.fluxo.conf.get().pluginId
version = libs.versions.version.get()

// FIXME: Find a way to deduplicate configuration between `self` and `plugin` modules.

kotlin {
    sourceSets.main {
        kotlin.srcDir("../$pluginDir/src/main/kotlin")
    }

    explicitApi()
    compilerOptions {
        @Suppress("MaxLineLength")
        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            // Kotlin's assignment overloading for Gradle plugins.
            // Lookup the Gradle repo for more details. Also:
            // https://stackoverflow.com/a/76022933/1816338.
            "-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading",
        )
        optIn.add("kotlin.contracts.ExperimentalContracts")
        suppressWarnings = true
    }
}

// https://github.com/gradle/gradle/blob/4817230/build-logic/kotlin-dsl/src/main/kotlin/gradlebuild.kotlin-dsl-sam-with-receiver.gradle.kts#L22
samWithReceiver {
    annotation(requireNotNull(HasImplicitReceiver::class.qualifiedName))
}

gradlePlugin {
    plugins {
        create("$pluginDir-buildSrc") {
            id = pluginId
            implementationClass = "fluxo.conf.FluxoKmpConfPlugin"
        }
    }
}

dependencies {
    implementation(libs.tomlj)
    // Spotless util classes required, used internally
    implementation(libs.plugin.spotless)
    // Detekt ReportMergeTask is used internally
    implementation(libs.plugin.detekt)
    // ASM for bytecode verification.
    implementation(libs.asm)

    implementation(platform(libs.okhttp.bom))

    compileOnly(libs.detekt.core)
    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.intellij)
    compileOnly(libs.plugin.jetbrains.compose)
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.ksp)

    compileOnly(libs.plugins.gradle.enterprise.toModuleDependency())
}

buildConfig {
    className("BuildConstants")
    packageName("fluxo.conf.data")
    buildConfigField("String", "PLUGIN_ID", "\"$pluginId\"")
    buildConfigField("int", "DEFAULT_ANDROID_MIN_SDK", libs.versions.androidMinSdk.get())
    buildConfigField("int", "DEFAULT_ANDROID_TARGET_SDK", libs.versions.androidTargetSdk.get())
    buildConfigField("int", "DEFAULT_ANDROID_COMPILE_SDK", libs.versions.androidCompileSdk.get())

    fun buildConfigField(
        name: String,
        p: Provider<PluginDependency>,
        alias: String? = null,
        alias2: String? = null,
        implementation: Boolean = false,
    ) {
        val aliasName = alias ?: name.lowercase().replace('_', '-')
        buildConfigField("String", "${name}_PLUGIN_ALIAS", "\"$aliasName\"")

        alias2?.let {
            buildConfigField("String", "${name}_PLUGIN_ALIAS2", "\"$it\"")
        }

        val pd = p.get()
        val pluginId = pd.pluginId
        buildConfigField("String", "${name}_PLUGIN_ID", "\"$pluginId\"")

        "${pd.version}".ifBlank { null }?.let { version ->
            buildConfigField("String", "${name}_PLUGIN_VERSION", "\"$version\"")
        }

        p.toModuleDependency().let { dependency ->
            when {
                implementation -> dependencies.implementation(dependency)
                else -> dependencies.compileOnly(dependency)
            }
        }
    }

    buildConfigField("KOTLIN_SAM_RECEIVER", libs.plugins.kotlin.sam.receiver)
    buildConfigField(
        "KOTLINX_BCV",
        libs.plugins.kotlinx.binCompatValidator,
        alias2 = "kotlinx-binCompatValidator",
    )
    buildConfigField("FLUXO_BCV_JS", libs.plugins.fluxo.bcv.js)
    buildConfigField("DOKKA", libs.plugins.dokka)
    buildConfigField("GRADLE_PLUGIN_PUBLISH", libs.plugins.gradle.plugin.publish)
    buildConfigField("COMPLETE_KOTLIN", libs.plugins.complete.kotlin)
    buildConfigField("DEPS_VERSIONS", libs.plugins.deps.versions, implementation = true)
    buildConfigField("DEPS_ANALYSIS", libs.plugins.deps.analysis)
    buildConfigField("DEPS_GUARD", libs.plugins.deps.guard, implementation = true)
    buildConfigField("TASK_TREE", libs.plugins.task.tree)
    buildConfigField("TASK_INFO", libs.plugins.task.info)
    buildConfigField("MODULE_DEPENDENCY_GRAPH", libs.plugins.module.dependency.graph)
    buildConfigField("BUILD_CONFIG", libs.plugins.build.config)

    fun buildConfigField(
        fieldName: String,
        p: Provider<MinimalExternalModuleDependency>,
        compileOnly: Boolean = true,
    ) {
        p.get().apply {
            buildConfigField("String", fieldName, "\"$group:$name:$version\"")
        }
        if (compileOnly) {
            dependencies.compileOnly(p)
        }
    }
    buildConfigField("PROGUARD_PLUGIN", libs.proguard.plugin)
    buildConfigField("PROGUARD_CORE", libs.proguard.core)
    buildConfigField("KOTLINX_METADATA_JVM", libs.kotlin.metadata.jvm)
    buildConfigField("R8", libs.r8)
}

if (project.hasProperty("buildScan")) {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}

fun Provider<PluginDependency>.toModuleDependency(): Provider<String> = map {
    it.toModuleDependency()
}

fun PluginDependency.toModuleDependency(): String {
    val version = version.toString()
    val v = if (version.isNotBlank()) ":$version" else ""
    return pluginId.let { "$it:$it.gradle.plugin$v" }
}
