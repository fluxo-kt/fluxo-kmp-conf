package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome

/**
 * The consumer shape where the Kotlin plugin sits on the root `buildscript` classpath and only
 * fluxo is in `plugins {}`. Fluxo resolves from the local Maven repository like any consumer's,
 * so the row's `kgpVersion` is the Kotlin plugin it runs against.
 *
 * Each case builds its own project directory, so they run as separate, concurrent tests: one
 * test per row ran them in a chain that alone outlasted the CI leg's budget.
 */
internal fun kotlinJvmConsumerCases(
    row: Map<String, String>,
    tempDir: Path,
): List<Pair<String, () -> Unit>> = buildList {
    add("lifecycle" to { runKotlinJvmLifecycle(row, tempDir) })
    add("deprecated-language-version" to { runDeprecatedLanguageVersionCase(row, tempDir) })
    add("consumer-settings-win" to { runConsumerSettingsWinCase(row, tempDir) })
    add("jvm-target" to { runKotlinJvmTargetCases(row, tempDir) })
    add("root-classpath-pin" to { runRootClasspathPinCase(row, tempDir) })
    add("fetched-tool" to { runFetchedToolCase(row, tempDir) })
    add("tool-injection" to { runToolInjectionCase(row, tempDir) })
    add("bundled-tool-versions" to { runBundledToolVersionCase(row, tempDir) })
    add("detekt-type-resolution" to { runDetektTypeResolutionCase(row, tempDir) })
    add("detekt-baseline-merge" to { runDetektBaselineMergeCase(row, tempDir) })
    add("taskinfo" to { runTaskInfoCase(row, tempDir) })
    if (row.kgpMinor() >= NEWEST_TESTED_KOTLIN) {
        add("dokka" to { runDokkaCase(row, tempDir) })
        add("kotlin-defaults-switch" to { runKotlinDefaultsSwitchCase(row, tempDir) })
        add("publication-setup-failure" to { runPublicationSetupFailureCase(row, tempDir) })
    }
}

/** The row's main project: its required tasks, dependency resolution and `FLUXO_EXPLAIN`. */
private fun runKotlinJvmLifecycle(row: Map<String, String>, tempDir: Path) {
    val writeProject = { projectDir: Path ->
        projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }
    fun case(tasks: List<String>, arguments: List<String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-consumer",
        tasks = tasks,
        arguments = arguments,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        writeProject = writeProject,
    ).output
    case(row.getValue("requiredTasks").split(' '), emptyList())
    // Resolves every configuration, so it must do so without reaching `Project` at execution.
    case(listOf("resolveDependencies"), emptyList())

    // FLUXO_EXPLAIN prints the derived settings at build end. The second run is a
    // configuration-cache hit, where configuration never runs, so the block must come from
    // the stored entry; without the flag nothing is printed.
    val explain = listOf("-PFLUXO_EXPLAIN=true")
    val derivedJvmTarget = "[:] jvmTarget = 17 ("
    for (run in listOf("configured", "cached")) {
        val output = case(HELP, explain)
        check(EXPLAIN_HEADER in output && derivedJvmTarget in output) {
            "FLUXO_EXPLAIN ($run run) must print '$derivedJvmTarget…':\n$output"
        }
        check((run == "cached") == (CONFIGURATION_CACHE_REUSED in output)) {
            "Only the second FLUXO_EXPLAIN run may reuse the configuration cache:\n$output"
        }
    }
    val plain = case(HELP, emptyList())
    check(EXPLAIN_HEADER !in plain) { "Without FLUXO_EXPLAIN no block is printed:\n$plain" }
}

/**
 * An optional tool the consumer didn't declare (here task-tree, requested by its task name) is
 * fetched by fluxo. Loaded into fluxo's own class loader, its tasks broke the configuration cache
 * ("could not be encoded"); Gradle must fetch it, so the cache stores the build and reuses it.
 */
private fun runFetchedToolCase(row: Map<String, String>, tempDir: Path) {
    for (run in listOf("stored", "reused")) {
        val result = runConsumerCase(
            row,
            tempDir,
            rootProjectName = "compat-kotlin-jvm-fetched-tool",
            projectDir = tempDir.resolve(row.getValue("id") + "-fetched-tool"),
            tasks = listOf("help", "taskTree"),
            forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
            // taskTree skips every other requested task by design.
            assertTasksSucceed = false,
        ) { projectDir ->
            projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
            writeKotlinJvmSources(projectDir)
        }
        val output = result.output
        result.assertTaskSuccess(":taskTree")
        check((run == "reused") == (CONFIGURATION_CACHE_REUSED in output)) {
            "Configuration cache must be $run:\n$output"
        }
    }
}

/**
 * taskinfo's newest release reads a Gradle-internal method signature that Gradle 9.8 dropped
 * (`NoSuchMethodError` in `tiTree`). Where it is gone fluxo skips the tool and says why; where it
 * exists (the 9.0 floor) `tiTree` must still work, so the probe can't over-skip.
 */
private fun runTaskInfoCase(row: Map<String, String>, tempDir: Path) {
    val supported = row.getValue("gradleVersion").startsWith("9.0")
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-taskinfo",
        projectDir = tempDir.resolve(row.getValue("id") + "-taskinfo"),
        tasks = listOf("tiTree", "help"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        expectFailure = if (supported) emptyList() else listOf("Gradle changed the internal API"),
        assertTasksSucceed = false,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }
    if (supported) result.assertTaskSuccess(":tiTree")
}

/**
 * A `pinned` catalog bundle can't change the root build classpath: Gradle loads it before any
 * plugin runs. Pinning it anyway only made `buildEnvironment` show versions that never ran (a
 * security pin reported as applied while the old jar was in use). The build must instead name
 * each module in use at an older version with the constraint line that does apply. okio is on
 * the root classpath (3.6.0) through fluxo's bundled Spotless; the pin is newer.
 */
private fun runRootClasspathPinCase(row: Map<String, String>, tempDir: Path) {
    val pin = "com.squareup.okio:okio:3.18.2"
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-pins",
        projectDir = tempDir.resolve(row.getValue("id") + "-pins"),
        tasks = listOf("buildEnvironment"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
        writeKotlinJvmSources(projectDir)
        projectDir.resolve("settings.gradle.kts").toFile().appendText(
            """

            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        library("okio", "$pin")
                        bundle("pinned", listOf("okio"))
                    }
                }
            }
            """.trimIndent(),
        )
    }.output
    check("classpath(\"$pin\") // in use: " in output) { "No root pin warning:\n$output" }
    check("-> 3.18.2" !in output) { "buildEnvironment shows a pin that never loaded:\n$output" }
}

/**
 * Runs `compileKotlin` on a separate copy of the Kotlin/JVM fixture with extra [setup] lines, so a
 * case that changes compiler settings never invalidates the main fixture's configuration cache.
 */
internal fun runKotlinJvmVariant(
    row: Map<String, String>,
    tempDir: Path,
    name: String,
    vararg setup: String,
    jdk: Int? = null,
    script: String = "",
    source: String? = null,
    javaSource: String? = null,
    sourceSet: String = "main",
    tasks: List<String> = listOf("compileKotlin"),
    arguments: List<String> = emptyList(),
    expectFailure: List<String> = emptyList(),
): Pair<Path, String> {
    val projectDir = tempDir.resolve("${row.getValue("id")}-$name")
    // The inner JDK is the row's `jdkVersion`, so a case on another JDK runs on a row copy;
    // the pin and its end-to-end check then cover that JDK unchanged.
    val output = runConsumerCase(
        if (jdk == null) row else row + ("jdkVersion" to jdk.toString()),
        tempDir,
        rootProjectName = "compat-kotlin-jvm-$name-consumer",
        projectDir = projectDir,
        tasks = tasks,
        arguments = arguments,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        expectFailure = expectFailure,
    ) {
        it.resolve("build.gradle.kts")
            .writeText(kotlinJvmConsumerBuildScript(row, *setup) + "\n" + script)
        writeKotlinJvmSources(it)
        // Rewritten or removed on every run, as cases share one project directory.
        val extras = listOf(
            "kotlin/compat/Extra.kt" to source,
            "java/compat/Extra.java" to javaSource,
        )
        for (set in listOf("main", "test")) {
            for ((file, text) in extras) {
                val path = it.resolve("src/$set/$file")
                if (set != sourceSet || text == null) {
                    path.deleteIfExists()
                } else {
                    path.parent.createDirectories()
                    path.writeText(text)
                }
            }
        }
    }.output
    return projectDir to output
}

internal fun Map<String, String>.kgpMinor(): KotlinVersion {
    // Only major and minor: the patch part may carry a prerelease suffix ("0-Beta1").
    val (major, minor) = getValue("kgpVersion").split('.').take(2).map(String::toInt)
    return KotlinVersion(major, minor)
}

/**
 * Language/API version 2.1 is in the supported range, and Kotlin 2.4 deprecates it, which is a
 * compiler warning: under warnings-as-errors a Kotlin upgrade alone would turn the build red.
 * The plugin suppresses that warning and prints one build-end warning naming the module; on a
 * Kotlin where 2.1 is not deprecated nothing is printed.
 */
private fun runDeprecatedLanguageVersionCase(row: Map<String, String>, tempDir: Path) {
    val (_, output) = runKotlinJvmVariant(
        row,
        tempDir,
        "lv21",
        "kotlinLangVersion = \"2.1\"",
        "kotlinApiVersion = \"2.1\"",
    )
    val deprecated = row.kgpMinor() >= FIRST_KOTLIN_DEPRECATING_2_1
    check((DEPRECATED_VERSION_WARNING in output) == deprecated) {
        "Expected the deprecated-version warning only when KGP deprecates 2.1:\n$output"
    }
}

private const val DEPRECATED_VERSION_WARNING = "Kotlin language/API version 2.1 is deprecated"

/** The first Kotlin release that deprecates language version 2.1. */
@Suppress("MagicNumber")
private val FIRST_KOTLIN_DEPRECATING_2_1 = KotlinVersion(2, 4)

private val HELP = listOf("help")

/** Copy of `fluxo.log.EXPLAIN_HEADER`: this test source set cannot see the plugin's internals. */
private const val EXPLAIN_HEADER = "fluxo-kmp-conf derived settings (FLUXO_EXPLAIN):"

/** [setup] lines go inside `fkcSetupKotlin {}`, after the fixture's own settings. */
private fun kotlinJvmConsumerBuildScript(
    row: Map<String, String>,
    vararg setup: String,
): String =
    """
    buildscript {
        repositories {
            google()
            gradlePluginPortal()
            mavenCentral()
        }
        dependencies {
            classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${row.getValue("kgpVersion")}")
        }
    }

    plugins {
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupKotlin {
        setupVerification = false
        enablePublication = false
        enableGradleDoctor = false
        setupCoroutines = false
        SETUP_SLOT
    }

    dependencies {
        add(
            "testImplementation",
            "org.jetbrains.kotlin:kotlin-test-junit5:${row.getValue("kgpVersion")}",
        )
    }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
    }
    """.trimIndent().replace("SETUP_SLOT", setup.joinToString("\n    "))

internal fun runKotlinJvmTestsDisabledConsumer(row: Map<String, String>, tempDir: Path) {
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-tests-disabled-consumer",
        arguments = listOf("-PDISABLE_TESTS=true"),
        seedBaseline = false,
        assertTasksSucceed = false,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKotlinJvmBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }
    check(result.task(":test")?.outcome != TaskOutcome.SUCCESS) {
        result.output
    }
    check(result.task(":compileTestKotlin")?.outcome != TaskOutcome.SUCCESS) {
        result.output
    }
    row.getValue("requiredTasks").split(' ').forEach {
        check(result.task(":$it") != null) {
            result.output
        }
    }
}

internal fun runKotlinJvmMarkerConsumer(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve("${row.getValue("id")}-marker")
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-marker-consumer",
        projectDir = projectDir,
    ) {
        it.resolve("build.gradle.kts").writeText(markerKotlinJvmBuildScript(row))
        writeKotlinJvmSources(it)
    }
    assertNoForbiddenResolvedClasspathLeaks(projectDir)
}
