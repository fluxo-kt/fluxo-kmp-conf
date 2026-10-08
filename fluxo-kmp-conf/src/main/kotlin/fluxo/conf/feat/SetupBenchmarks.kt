package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.impl.kotlin.KOTLIN_PLUGIN_VERSION_STRING
import fluxo.conf.impl.kotlin.setupTargets
import fluxo.log.logDecision
import fluxo.log.w
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.HostManager

/**
 * A module that applies kotlinx-benchmark or androidx.benchmark only measures code: it ships
 * nothing. So publication, explicit API mode, API validation and Dependency Guard default to off
 * there, even when
 * the root turns them on for every module; a value the module sets itself still wins.
 */
internal val Project.isBenchmarkModule: Boolean
    get() = BENCHMARK_PLUGIN_IDS.any { pluginManager.hasPlugin(it) }

/**
 * Does for a kotlinx-benchmark module what its setup guide asks of every user:
 * - registers each target it can run: JVM, and the native target of the build host (a native
 *   benchmark is an executable, so other hosts' targets can't run here);
 * - applies all-open for JMH's `@State`, as JMH subclasses benchmark classes;
 * - adds `kotlinx-benchmark-runtime` with the plugin's own version as a preference, so a
 *   version the module declares wins.
 * androidx.benchmark needs nothing: its plugin sets the benchmark runner and release test
 * build type itself.
 */
internal fun Project.setupBenchmarks(ctx: FluxoKmpConfContext) {
    pluginManager.withPlugin(KOTLINX_BENCHMARK_PLUGIN_ID) { setupKotlinxBenchmark(ctx) }
}

private fun Project.setupKotlinxBenchmark(ctx: FluxoKmpConfContext) {
    val kotlin = extensions.getByType(KotlinProjectExtension::class.java)
    // Reflection: the plugin lives in this module's class loader, which fluxo's classes can't
    // see, and `getTargets` is its public, stable DSL getter.
    val extension = extensions.getByName(KOTLINX_BENCHMARK_EXTENSION)
    val targets = extension.javaClass.getMethod("getTargets")
        .invoke(extension) as NamedDomainObjectContainer<*>
    val isKmp = kotlin is KotlinMultiplatformExtension
    kotlin.setupTargets {
        val runsHere = when (platformType) {
            KotlinPlatformType.jvm -> true

            KotlinPlatformType.native ->
                (this as KotlinNativeTarget).konanTarget == HostManager.host

            else -> false
        }
        // A single-target module's benchmarks live in its `main` source set.
        val name = if (isKmp) name else MAIN
        if (runsHere && targets.findByName(name) == null) targets.register(name)
    }

    val allOpenApplied = ctx.loadAndApplyPluginIfNotApplied(
        id = ALL_OPEN_PLUGIN_ID,
        className = ALL_OPEN_PLUGIN_CLASS,
        // A Kotlin compiler plugin must match the consumer's Kotlin, as for sam-with-receiver.
        version = KOTLIN_PLUGIN_VERSION_STRING,
        project = this,
        loadedWorksWithCache = true,
    ).applied
    if (allOpenApplied) {
        val allOpen = extensions.getByName(ALL_OPEN_EXTENSION)
        allOpen.javaClass.getMethod("annotation", String::class.java).invoke(allOpen, JMH_STATE)
    } else {
        logger.w(
            "$path: all-open could not be applied, so JMH rejects final @State classes. Add " +
                "id(\"$ALL_OPEN_PLUGIN_ID\") with allOpen { annotation(\"$JMH_STATE\") }.",
        )
    }

    val version = kotlinxBenchmarkVersion()
    val sourceSet = if (isKmp) COMMON_MAIN else MAIN
    if (version == null) {
        logger.w(
            "$path: kotlinx-benchmark's version is unknown, so its runtime is not added. Add " +
                "\"$KOTLINX_BENCHMARK_RUNTIME:<plugin version>\" to $sourceSet.",
        )
    } else {
        val configuration = kotlin.sourceSets.getByName(sourceSet).implementationConfigurationName
        (dependencies.add(configuration, KOTLINX_BENCHMARK_RUNTIME) as ExternalModuleDependency)
            .version { prefer(version) }
    }
    ctx.logDecision(
        this,
        setting = "Benchmark module",
        value = "kotlinx-benchmark ${version ?: "?"}",
        reason = "$KOTLINX_BENCHMARK_PLUGIN_ID is applied",
        howToChange = "set enablePublication/enableApiValidation in this module to override",
    )
}

/**
 * The applied plugin's version: from its jar manifest (0.4.17+), else from its jar file name,
 * as Gradle's caches keep `<artifact>-<version>.jar`.
 */
private fun Project.kotlinxBenchmarkVersion(): String? {
    val pluginClass = plugins.getPlugin(KOTLINX_BENCHMARK_PLUGIN_ID).javaClass
    return pluginClass.`package`?.implementationVersion
        ?: pluginClass.protectionDomain?.codeSource?.location?.path
            ?.substringAfterLast('/')
            ?.removePrefix("kotlinx-benchmark-plugin-")?.removeSuffix(".jar")
            ?.takeIf { it.firstOrNull()?.isDigit() == true }
}

private const val KOTLINX_BENCHMARK_PLUGIN_ID = "org.jetbrains.kotlinx.benchmark"

private val BENCHMARK_PLUGIN_IDS = arrayOf(KOTLINX_BENCHMARK_PLUGIN_ID, "androidx.benchmark")

private const val KOTLINX_BENCHMARK_EXTENSION = "benchmark"

private const val KOTLINX_BENCHMARK_RUNTIME = "org.jetbrains.kotlinx:kotlinx-benchmark-runtime"

private const val ALL_OPEN_PLUGIN_ID = "org.jetbrains.kotlin.plugin.allopen"

/** @see org.jetbrains.kotlin.allopen.gradle.AllOpenGradleSubplugin */
private const val ALL_OPEN_PLUGIN_CLASS =
    "org.jetbrains.kotlin.allopen.gradle.AllOpenGradleSubplugin"

private const val ALL_OPEN_EXTENSION = "allOpen"

/** kotlinx-benchmark's common `@State` is this annotation on JVM. */
private const val JMH_STATE = "org.openjdk.jmh.annotations.State"

private const val MAIN = "main"

private const val COMMON_MAIN = "commonMain"
