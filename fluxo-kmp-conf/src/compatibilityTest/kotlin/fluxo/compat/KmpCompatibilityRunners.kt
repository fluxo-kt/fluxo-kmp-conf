package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
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
        runKmpBareSetupCase(row, tempDir)
        runKmpBrowserTestsCase(row, tempDir)
    }
}

/**
 * JS tests run in a real browser where Karma finds Chrome, and are skipped with a warning where
 * it finds none, so `check` stays green there. The browser setup was once skipped for every
 * target (a type check meant for WASI matched JS too), so no fluxo build ran browser tests.
 * The run without Chrome reuses the first run's configuration cache: the browser lookup must
 * happen when the task runs, not be stored with the configuration. Needs Chrome on the host.
 */
private fun runKmpBrowserTestsCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve("${row.getValue("id")}-browser-tests")
    val task = ":jsBrowserTest"
    fun run(environment: Map<String, String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-browser-tests",
        projectDir = projectDir,
        tasks = listOf(task.removePrefix(":")),
        assertTasksSucceed = false,
        environment = environment,
    ) {
        // Running JS tests downloads Node.js and Yarn from repositories KGP adds to the project.
        val settings = it.resolve("settings.gradle.kts")
        settings.writeText(settings.readText().replace("FAIL_ON_PROJECT_REPOS", "PREFER_PROJECT"))
        it.resolve("build.gradle.kts").writeText(
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
                },
                kmp = { js() },
                kotlin = { sourceSets.commonTest.dependencies { implementation(kotlin("test")) } },
            )
            """.trimIndent(),
        )
        it.resolve("src/commonTest/kotlin").createDirectories().resolve("BrowserTest.kt")
            .writeText(
                "package compat\n\nimport kotlin.test.Test\nimport kotlin.test.assertEquals\n\n" +
                    "class BrowserTest {\n    @Test\n    fun adds() = assertEquals(2, 1 + 1)\n}\n",
            )
    }

    val withChrome = run(emptyMap())
    check(withChrome.task(task)?.outcome == TaskOutcome.SUCCESS) {
        "$task did not run (the host needs Chrome for this case)\n${withChrome.output}"
    }
    val results = projectDir.resolve("build/test-results/jsBrowserTest").toFile()
    // The results dir also holds a `binary/` directory; only the XML reports name the tests.
    val reports = results.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(".xml") }
    check(reports.any { "adds" in it.readText() }) {
        "$task ran no test: no result for 'adds' in $results"
    }

    val noChrome = run(mapOf("CHROME_BIN" to projectDir.resolve("no-chrome").toString()))
    check(noChrome.task(task)?.outcome == TaskOutcome.SKIPPED) {
        "$task without Chrome was ${noChrome.task(task)?.outcome}\n${noChrome.output}"
    }
    check("$task skipped: no Chrome found" in noChrome.output) { noChrome.output }
}

/**
 * `fkcSetupMultiplatform()` without a `kmp`, `kotlin` or `android` block must still set the module
 * up, like every other `fkcSetup*()`: it once returned early, so the module got no fluxo setup and
 * its `defaults {}` (its own or a parent's) created no targets.
 */
private fun runKmpBareSetupCase(row: Map<String, String>, tempDir: Path) {
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-bare-setup",
        projectDir = tempDir.resolve("${row.getValue("id")}-bare-setup"),
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
                    defaults { jvm() }
                },
            )

            println("$BARE_SETUP_MARKER" + kotlin.targets.names.sorted())
            """.trimIndent(),
        )
    }.output
    check("$BARE_SETUP_MARKER[jvm, metadata]" in output) {
        "Bare fkcSetupMultiplatform() did not create the default jvm target:\n$output"
    }
}

private const val BARE_SETUP_MARKER = "FLUXO_COMPAT_BARE_SETUP_TARGETS="

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
 * KGP's one JS/Wasm target class implements the WASI DSL too, so a classifier keyed on that type
 * calls Wasm-JS WASI: the consumer-declared wasmJs must be disabled here (fluxo creates none
 * under this filter).
 * An enabled compile succeeds, a disabled one is SKIPPED. The compile also guards the flags fluxo
 * passes to Wasm: Kotlin 2.4 compiles Wasm with its own argument set, where a JS-only flag prints
 * "Flag is not supported by this version of the compiler" on every compile.
 */
private fun runKmpWasiFilterCase(row: Map<String, String>, tempDir: Path) {
    val task = ":compileKotlinWasmWasi"
    val wasmJsTask = ":compileKotlinWasmJs"
    val kgp = row.getValue("kgpVersion")
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-all-targets-consumer",
        projectDir = tempDir.resolve("${row.getValue("id")}-all-targets-$kgp"),
        tasks = listOf(task.removePrefix(":"), wasmJsTask.removePrefix(":")),
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
                kotlin { wasmJs { nodejs() } }
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
    val wasmJsOutcome = result.task(wasmJsTask)?.outcome
    check(wasmJsOutcome == TaskOutcome.SKIPPED) {
        "KMP_TARGETS=WASM_WASI: $wasmJsTask was $wasmJsOutcome\n${result.output}"
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
