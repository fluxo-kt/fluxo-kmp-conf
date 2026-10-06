package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome

internal fun runKmpConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-consumer",
        arguments = listOf("-PKMP_TARGETS=JVM"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpBuildScript(row))
        writeKmpSources(projectDir)
    }
    runKmpJsCase(row, tempDir)
    // Kotlin keeps deprecating native targets and then deletes them (watchosArm32 has no DSL
    // method and no KonanTarget left in Kotlin 2.5), so target groups follow the consumer's
    // Kotlin, never a list in the plugin. The newest row also runs on the next Kotlin.
    if (row.getValue("kotlinLangVersion") == "-") {
        runKmpAllTargetsCase(row, tempDir)
        // Already on it in the newest-upstream run; the same project again would only reuse
        // the configuration cache and print none of the markers.
        if (row["kgpVersion"] != NEXT_KOTLIN) {
            runKmpAllTargetsCase(row + ("kgpVersion" to NEXT_KOTLIN), tempDir)
        }
        runKmpWasiFilterCase(row, tempDir)
    }
    if (row.kgpMinor() >= NEWEST_TESTED_KOTLIN) {
        runKmpTsApiChecksCase(row, tempDir)
        runKmpShortOptInCase(row, tempDir)
        runKmpStdlibSplitCase(row, tempDir)
        runKmpNpmToolVersionCase(row, tempDir)
    }
}

/**
 * TypeScript ABI checks (fluxo-bcv-js) read the Kotlin plugin's JS DSL, so the plugin must load
 * where it can see the Kotlin plugin. Fetched through a Gradle script plugin it could not: its
 * class initialiser failed with `NoClassDefFoundError: …/KotlinJsTargetDsl`. The settings plugin
 * now puts it on the module's build classpath; with the configuration cache on it must apply.
 */
private fun runKmpTsApiChecksCase(row: Map<String, String>, tempDir: Path) {
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-ts-api",
        projectDir = tempDir.resolve("${row.getValue("id")}-ts-api"),
        tasks = listOf("help"),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}"
                id("${pluginId()}") version "${pluginVersion()}"
            }

            fkcSetupMultiplatform(
                config = {
                    setupVerification = false
                    enablePublication = false
                    enableGradleDoctor = false
                    setupCoroutines = false
                    apiValidation { tsApiChecks = true }
                },
                kmp = { jvm(); js() },
            )

            println("$TS_API_MARKER" + plugins.hasPlugin("$FLUXO_BCV_JS_ID"))
            """.trimIndent(),
        )
    }.output
    check("${TS_API_MARKER}true" in output) { "fluxo-bcv-js not applied:\n$output" }
}

private const val TS_API_MARKER = "FLUXO_COMPAT_TS_API="
private const val FLUXO_BCV_JS_ID = "io.github.fluxo-kt.binary-compatibility-validator-js"

/**
 * `KMP_TARGETS=WASM_WASI` must keep the WASI target and its tests enabled: fluxo disables every
 * target outside the filter, so a WASI target classified as Wasm-JS would disable the very target
 * asked for.
 * An enabled compile succeeds, a disabled one is SKIPPED. The compile also guards the flags fluxo
 * passes to Wasm: Kotlin 2.4 compiles Wasm with its own argument set, where a JS-only flag prints
 * "Flag is not supported by this version of the compiler" on every compile.
 */
private fun runKmpWasiFilterCase(row: Map<String, String>, tempDir: Path) {
    val task = ":compileKotlinWasmWasi"
    val kgp = row.getValue("kgpVersion")
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-all-targets-consumer",
        projectDir = tempDir.resolve("${row.getValue("id")}-all-targets-$kgp"),
        tasks = listOf(task.removePrefix(":")),
        arguments = listOf("-PKMP_TARGETS=WASM_WASI"),
        forbiddenOutput = listOf("Flag is not supported by this version of the compiler"),
        assertTasksSucceed = false,
    ) { projectDir ->
        // WASI is not among the default targets: consumers declare it.
        val wasi = "; wasmWasi { target { nodejs() } }"
        val script = markerKmpAllTargetsBuildScript(row, extraTargets = wasi)
        projectDir.resolve("build.gradle.kts").writeText(
            script + "\n" +
                """
                gradle.projectsEvaluated {
                    tasks.withType<org.gradle.api.tasks.testing.AbstractTestTask>().forEach {
                        println("$TEST_TASK_MARKER" + it.name + ":" + it.enabled)
                    }
                }
                """.trimIndent(),
        )
        projectDir.resolve("src/wasmWasiMain/kotlin").createDirectories()
            .resolve("W.kt").writeText("package compat\n\nfun wasi(): Int = 1\n")
    }
    check(result.task(task)?.outcome == TaskOutcome.SUCCESS) {
        "KMP_TARGETS=WASM_WASI: $task was ${result.task(task)?.outcome}\n${result.output}"
    }
    // Wasm tests are KotlinJsTest tasks too, so a gate keyed on the JS target disabled them.
    val wasiTests = result.output.lines()
        .filter { it.startsWith(TEST_TASK_MARKER + "wasmWasi") }
    check(wasiTests.isNotEmpty() && wasiTests.all { it.endsWith(":true") }) {
        "KMP_TARGETS=WASM_WASI: WASI test tasks $wasiTests\n${result.output}"
    }
}

private const val TEST_TASK_MARKER = "FLUXO_COMPAT_TEST_TASK="

/**
 * Groups like `allDefaultTargets()` must create only targets the consumer's Kotlin fully
 * supports: a deprecated target warns (and becomes an error once Kotlin stops tolerating it),
 * and a removed one has no DSL method left, so calling it fails the build with
 * NoSuchMethodError; the next-Kotlin run covers that. iosX64 is left out too (ruled
 * 2026-10-04: Compose Multiplatform dropped it). The arm64 targets are the control: without
 * them the case would also pass with no native targets at all.
 */
private fun runKmpAllTargetsCase(row: Map<String, String>, tempDir: Path) {
    val kgp = row.getValue("kgpVersion")
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-all-targets-consumer",
        projectDir = tempDir.resolve("${row.getValue("id")}-all-targets-$kgp"),
        tasks = listOf("help"),
        arguments = listOf("-PKMP_TARGETS_ALL=true"),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpAllTargetsBuildScript(row))
    }.output
    fun printed(marker: String) = output.substringAfter(marker, "").substringBefore('\n')
    val targets = printed(KMP_TARGETS_MARKER).split(',')
    check(targets.containsAll(listOf("iosArm64", "watchosArm64"))) { "No native targets:\n$output" }
    val deprecated = printed(KMP_DEPRECATED_TARGETS_MARKER)
    check(deprecated.isEmpty()) { "Kotlin $kgp: groups created deprecated $deprecated:\n$output" }
    check("iosX64" !in targets) { "Kotlin $kgp: groups created iosX64:\n$output" }
    // The fixture has no Android Gradle Plugin: the Android target is dropped with guidance
    // instead of failing configuration.
    check(AGP_MISSING in output) { "No AGP guidance:\n$output" }
}

private const val AGP_MISSING = "Android Gradle Plugin (AGP) is not found in the classpath"

/** The next Kotlin release line, Beta included: removals land in its first Beta. */
private const val NEXT_KOTLIN = "2.5.0-Beta1"

internal fun runKmpCommonOnlyConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-common-only-consumer",
        arguments = listOf("-PKMP_TARGETS=COMMON"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpCommonOnlyBuildScript(row))
    }
}

internal fun runKmpInvalidTargetConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-invalid-target-consumer",
        arguments = listOf("-PKMP_TARGETS=TYPO"),
        expectFailure = listOf(
            "KMP_TARGETS property of 'TYPO' not recognized",
            "Known options are:",
            "ANDROID",
            "IOS_SIMULATOR_ARM64",
        ),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpBuildScript(row))
    }
}
