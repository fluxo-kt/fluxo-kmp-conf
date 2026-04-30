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

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("../$pluginDir/src/main/kotlin")
    }

    explicitApi()
    compilerOptions {
        @Suppress("MaxLineLength")
        freeCompilerArgs.addAll(
            // Kotlin's assignment overloading for Gradle plugins.
            // Lookup the Gradle repo for more details. Also:
            // https://stackoverflow.com/a/76022933/1816338.
            "-P=plugin:org.jetbrains.kotlin.assignment:annotation=org.gradle.api.SupportsKotlinAssignmentOverloading",
        )
        optIn.add("kotlin.contracts.ExperimentalContracts")
        suppressWarnings = true
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
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
    // Mirrored with `fluxo-kmp-conf/build.gradle.kts` — the region between the
    // MIRROR-START / MIRROR-END markers must stay byte-identical in both files.
    // Verified by the `verifyBuildScriptMirror` task (runs as part of `check`).
    // MIRROR-START
    implementation(libs.tomlj)
    // Spotless util classes are used internally
    implementation(libs.plugin.spotless)
    // Detekt ReportMergeTask is used internally
    implementation(libs.plugin.detekt)
    // `kotlin-compiler-embeddable` is `compileOnly` so it stays off the published plugin's
    // runtime classpath. It conflicts with KGP-bundled compiler internals on the consumer's
    // buildscript classpath (KGP since 2.1.0 no longer drags it in transitively, and the
    // synthetic `BuildPerformanceMetrics.add$default` and similar helpers diverge between
    // Kotlin patch versions). When the consumer is on AGP 9 with built-in Kotlin (KGP 2.2.10
    // bundled) plus our pinned 2.2.21, Gradle's `<latest>` resolution picks 2.2.21 for
    // `kotlin-compiler-embeddable` while KGP itself stays at 2.2.10 → `NoSuchMethodError`
    // from `GradleCompilationResults` on `compileDebugKotlin`. Compile-time access is enough
    // because at runtime KGP brings `kotlin-tooling-core` (where `KotlinToolingVersion` lives)
    // and Detekt brings `kotlin-compiler-embeddable` transitively at the consumer-applied
    // version, so reflective callers see a self-consistent classpath.
    compileOnly(libs.kotlin.compiler.embeddable)
    // `detekt-core` is `compileOnly`: the only direct compile-time use is the
    // `io.github.detekt.tooling.api.BaselineProvider` import in `MergeDetektBaselinesTask`,
    // plus the reflective fallback resolved via `Class.forName`. Consumer builds that apply
    // the Detekt plugin pull detekt-core transitively via detekt-gradle-plugin, so the
    // consumer-facing runtime surface stays self-consistent without us republishing it.
    compileOnly(libs.detekt.core)
    // ASM for bytecode verification.
    implementation(libs.asm)

    implementation(platform(libs.okhttp.bom))

    compileOnly(libs.ktlint)

    compileOnly(libs.plugin.android)
    compileOnly(libs.plugin.intellij)
    compileOnly(libs.plugin.jetbrains.compose)
    compileOnly(libs.plugin.kotlin)
    compileOnly(libs.plugin.kotlin.compose)
    compileOnly(libs.plugin.ksp)

    compileOnly(libs.plugins.develocity.toModuleDependency())
    compileOnly(libs.plugins.kotlin.sam.receiver.toModuleDependency())
    compileOnly(libs.plugins.kotlinx.binCompatValidator.toModuleDependency())
    compileOnly(libs.plugins.vanniktech.mvn.publish.toModuleDependency())
    compileOnly(libs.plugins.fluxo.bcv.js.toModuleDependency())
    // MIRROR-END
}

buildConfig {
    // MIRROR-START
    className("BuildConstants")
    packageName("fluxo.conf.data")
    buildConfigField("String", "PLUGIN_ID", "\"$pluginId\"")

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
    // MIRROR-END
}

develocity {
    // https://docs.gradle.com/develocity/gradle-plugin/current/
    buildScan {
        publishing.onlyIf { false }
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")
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
