package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * A module applying kotlinx-benchmark is a benchmark module with no further setup: its JVM
 * benchmark jar builds (JMH needs the target registered, `@State` classes open and the runtime
 * library), and it ships nothing, although the root asks every module to publish and check its
 * API.
 */
internal fun runKmpBenchmarkPresetCase(row: Map<String, String>, tempDir: Path) {
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-benchmark",
        projectDir = tempDir.resolve(row.getValue("id") + "-benchmark"),
        tasks = listOf("bench:jvmBenchmarkJar"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { dir ->
        dir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript("compat-kmp-benchmark") + "\ninclude(\":bench\")\n",
        )
        dir.resolve("build.gradle.kts").writeText(rootScript(row))
        val bench = dir.resolve("bench")
        bench.resolve("src/commonMain/kotlin/compat").createDirectories()
            .resolve("Sum.kt").writeText(BENCHMARK_SOURCE)
        bench.resolve("build.gradle.kts").writeText(BENCH_SCRIPT)
    }.output

    // The module's effective settings, read where fluxo reads them: the root's library
    // defaults (publication, API validation, explicit API) must not reach it.
    val settings = output.lines().single { it.startsWith(SETTINGS_MARKER) }
        .removePrefix(SETTINGS_MARKER)
    check(settings == "[false, false, Disabled]") {
        "A benchmark module must ship nothing, yet has [publication, API check, explicit API] " +
            "= $settings"
    }
}

private const val SETTINGS_MARKER = "FLUXO_COMPAT_BENCH_SETTINGS "

private const val BENCHMARK_SOURCE = """package compat

import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State

@State(Scope.Benchmark)
class Sum {
    @Benchmark
    fun sum(): Int = 1 + 1
}
"""

/** kotlinx-benchmark 0.4.17+ needs Kotlin 2.2; 0.4.16 is the last line for Kotlin 2.1. */
private fun Map<String, String>.benchmarkVersion() =
    if (kgpMinor() >= KotlinVersion(2, 2)) "0.5.0" else "0.4.16"

private fun rootScript(row: Map<String, String>) =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}" apply false
        id("org.jetbrains.kotlinx.benchmark") version "${row.benchmarkVersion()}" apply false
        id("${pluginId()}") version "${pluginVersion()}"
    }

    fluxoConfiguration {
        configureAsLibrary()
        enablePublication = true
    }
    """.trimIndent()

private val BENCH_SCRIPT =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform")
        id("org.jetbrains.kotlinx.benchmark")
    }

    fkcSetupMultiplatform(
        config = {
            enableGradleDoctor = false
            setupCoroutines = false
            println("$SETTINGS_MARKER" + listOf(enablePublication, enableApiValidation, explicitApi))
        },
        kmp = { jvm() },
    )
    """.trimIndent()
