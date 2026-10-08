package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome

/**
 * Each case builds its own project directory, so they run as separate, concurrent tests: one test
 * per row ran them in a chain that alone outlasted the CI leg's budget.
 */
internal fun kmpConsumerCases(
    row: Map<String, String>,
    tempDir: Path,
): List<Pair<String, () -> Unit>> = buildList {
    add("lifecycle" to { runKmpLifecycle(row, tempDir) })
    add("js" to { runKmpJsCase(row, tempDir) })
    add("common-compile-only" to { runKmpCommonCompileOnlyCase(row, tempDir) })
    add("multi-release" to { runKmpMultiReleaseJarCase(row, tempDir) })
    // Kotlin keeps deprecating native targets and then deletes them (watchosArm32 has no DSL
    // method and no KonanTarget left in Kotlin 2.5), so target groups follow the consumer's
    // Kotlin, never a list in the plugin. The newest row also runs on the next Kotlin.
    if (row.getValue("kotlinLangVersion") == "-") {
        // The WASI filter case rebuilds the all-targets project, so it runs after it, not beside.
        add(
            "all-targets" to {
                runKmpAllTargetsCase(row, tempDir)
                runKmpWasiFilterCase(row, tempDir)
            },
        )
        // Already on it in the newest-upstream run; the same project again would only reuse
        // the configuration cache and print none of the markers.
        if (row["kgpVersion"] != NEXT_KOTLIN) {
            val next = row + ("kgpVersion" to NEXT_KOTLIN)
            add("all-targets-next" to { runKmpAllTargetsCase(next, tempDir) })
        }
    }
    if (row.kgpMinor() >= NEWEST_TESTED_KOTLIN) {
        add("short-opt-in" to { runKmpShortOptInCase(row, tempDir) })
        add("coroutines-opt-ins" to { runKmpCoroutinesOptInCase(row, tempDir) })
        add("stdlib-split" to { runKmpStdlibSplitCase(row, tempDir) })
        add("npm-tool-version" to { runKmpNpmToolVersionCase(row, tempDir) })
        add("bare-setup" to { runKmpBareSetupCase(row, tempDir) })
        add("browser-tests" to { runKmpBrowserTestsCase(row, tempDir) })
        for (kgp in KGP_ABI_LINES + row.getValue("kgpVersion")) {
            add("kgp-abi-$kgp" to { runKgpAbiCase(row, tempDir, kgp, jvm = false) })
        }
        // Only 2.2/2.3 reach Kotlin's engine through reflection, which differs on Kotlin/JVM (no
        // klib member) and which bcv-ts reads; 2.4 is typed, so compile-checked.
        for (kgp in KGP_ABI_LINES) {
            add("kgp-abi-jvm-$kgp" to { runKgpAbiCase(row, tempDir, kgp, jvm = true) })
        }
        add("ts-api" to { runKmpTsApiChecksCase(row, tempDir, KGP_ABI_LINES.first()) })
    }
}

private fun runKmpLifecycle(row: Map<String, String>, tempDir: Path) {
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
}

/**
 * JS tests run in a real browser where Karma finds Chrome, and are skipped with a warning where
 * it finds none, so `check` stays green there. The browser setup was once skipped for every
 * target (a type check meant for WASI matched JS too), so no fluxo build ran browser tests.
 * The run without Chrome reuses the first run's configuration cache: the browser lookup must
 * happen when the task runs, not be stored with the configuration. Needs Chrome on the host.
 * The Wasm-JS test executable compile is the only compile receiving fluxo's main-function
 * arguments flag, so it carries the unsupported-flag check for Wasm.
 */
private fun runKmpBrowserTestsCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve("${row.getValue("id")}-browser-tests")
    val task = ":jsBrowserTest"
    fun run(environment: Map<String, String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-browser-tests",
        projectDir = projectDir,
        tasks = listOf(task.removePrefix(":"), "compileTestDevelopmentExecutableKotlinWasmJs"),
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
                kmp = { js(); wasmJs() },
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
 * Its `.d.ts` dump must land next to Kotlin's on every Kotlin line's ABI engine: it follows
 * Kotlin's own from 2.2 (fluxo's default there), so each line runs here.
 */
private fun runKmpTsApiChecksCase(row: Map<String, String>, tempDir: Path, kgp: String) {
    val kgpRow = row + ("kgpVersion" to kgp)
    val projectDir = tempDir.resolve("${row.getValue("id")}-ts-api-$kgp")
    val output = runConsumerCase(
        kgpRow,
        tempDir,
        rootProjectName = "compat-kmp-ts-api",
        projectDir = projectDir,
        tasks = listOf("apiDump"),
    ) { dir ->
        // Dumping JS API sets up Node.js, from a repository KGP adds to the project.
        val settings = dir.resolve("settings.gradle.kts")
        settings.writeText(settings.readText().replace("FAIL_ON_PROJECT_REPOS", "PREFER_PROJECT"))
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("org.jetbrains.kotlin.multiplatform") version "$kgp"
                id("${pluginId()}") version "${pluginVersion()}"
            }

            fkcSetupMultiplatform(
                config = {
                    setupVerification = false
                    enablePublication = false
                    enableGradleDoctor = false
                    setupCoroutines = false
                    enableApiValidation = true
                    apiValidation { tsApiChecks = true }
                },
                kmp = { jvm(); js() },
            )

            println("$TS_API_MARKER" + plugins.hasPlugin("$FLUXO_BCV_JS_ID"))
            """.trimIndent(),
        )
        dir.resolve("src/commonMain/kotlin").createDirectories().resolve("A.kt").writeText(
            "package compat\n\nclass Api {\n    fun f(): Int = 1\n}\n",
        )
        dir.resolve("src/jsMain/kotlin").createDirectories().resolve("Js.kt").writeText(
            "package compat\n\n@JsExport\nclass JsApi {\n    fun g(): Int = 2\n}\n",
        )
    }.output
    check("${TS_API_MARKER}true" in output) { "fluxo-bcv-js not applied:\n$output" }
    val dumps = projectDir.resolve("api").toFile().walk().filter { it.isFile }
        .associate { it.relativeTo(projectDir.toFile()).invariantSeparatorsPath to it.readText() }
    // The klib dump lists JsApi too, so only a `.d.ts` dump naming it proves the TypeScript check.
    val tsDump = dumps.filterKeys { it.endsWith(".d.ts") }.values
    check(dumps.keys.any { it.endsWith(".klib.api") } && tsDump.any { "JsApi" in it }) {
        "Kotlin $kgp: no klib dump or no TypeScript dump naming JsApi: ${dumps.keys}\n$output"
    }
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
 * An enabled compile succeeds, a disabled one is SKIPPED. The production link is where JS-only
 * options reach WASI: TypeScript declarations there crashed the Kotlin 2.4 compiler.
 */
private fun runKmpWasiFilterCase(row: Map<String, String>, tempDir: Path) {
    val task = ":compileKotlinWasmWasi"
    val linkTask = ":compileProductionExecutableKotlinWasmWasi"
    val wasmJsTask = ":compileKotlinWasmJs"
    val kgp = row.getValue("kgpVersion")
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-all-targets-consumer",
        projectDir = tempDir.resolve("${row.getValue("id")}-all-targets-$kgp"),
        tasks = listOf(task, linkTask, wasmJsTask).map { it.removePrefix(":") },
        arguments = listOf("-PKMP_TARGETS=WASM_WASI"),
        assertTasksSucceed = false,
    ) { projectDir ->
        // WASI is not among the default targets: consumers declare it. Plain `wasmWasi()` gets
        // fluxo's default target setup (a `target {}` block replaces it).
        val wasi = "; wasmWasi()"
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
    for (enabled in listOf(task, linkTask)) {
        check(result.task(enabled)?.outcome == TaskOutcome.SUCCESS) {
            "KMP_TARGETS=WASM_WASI: $enabled was ${result.task(enabled)?.outcome}\n${result.output}"
        }
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
