package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome

/**
 * The consumer shape where the Kotlin plugin sits on the root `buildscript` classpath and only
 * fluxo is in `plugins {}`. Fluxo resolves from the local Maven repository like any consumer's,
 * so the row's `kgpVersion` is the Kotlin plugin it runs against.
 */
internal fun runKotlinJvmConsumer(row: Map<String, String>, tempDir: Path) {
    val writeProject = { projectDir: Path ->
        projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }
    val case = { tasks: List<String>, arguments: List<String> ->
        runConsumerCase(
            row,
            tempDir,
            rootProjectName = "compat-kotlin-jvm-consumer",
            tasks = tasks,
            arguments = arguments,
            forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
            writeProject = writeProject,
        ).output
    }
    case(row.getValue("requiredTasks").split(' '), emptyList())

    // FLUXO_EXPLAIN prints the derived settings at build end. The second run is a
    // configuration-cache hit, where configuration never runs, so the block must come from
    // the stored entry; without the flag nothing is printed.
    val explain = listOf("-PFLUXO_EXPLAIN=true")
    val derivedJvmTarget = "[:] jvmTarget = ${row.getValue("jdkVersion")} ("
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

    runDeprecatedLanguageVersionCase(row, tempDir)
}

/**
 * Language/API version 2.1 is in the supported range, and Kotlin 2.4 deprecates it, which is a
 * compiler warning: under warnings-as-errors a Kotlin upgrade alone would turn the build red.
 * The plugin suppresses that warning and prints one build-end warning naming the module; on a
 * Kotlin where 2.1 is not deprecated nothing is printed.
 */
private fun runDeprecatedLanguageVersionCase(row: Map<String, String>, tempDir: Path) {
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-lv21-consumer",
        projectDir = tempDir.resolve("${row.getValue("id")}-lv21"),
        tasks = listOf("compileKotlin"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        val script = kotlinJvmConsumerBuildScript(row).replace(
            "    setupCoroutines = false\n",
            "    setupCoroutines = false\n" +
                "    kotlinLangVersion = \"2.1\"\n" +
                "    kotlinApiVersion = \"2.1\"\n",
        )
        check("kotlinLangVersion" in script) { "Build script anchor moved:\n$script" }
        projectDir.resolve("build.gradle.kts").writeText(script)
        writeKotlinJvmSources(projectDir)
    }.output
    val (major, minor) = row.getValue("kgpVersion").split('.').map(String::toInt)
    val deprecated = KotlinVersion(major, minor) >= FIRST_KOTLIN_DEPRECATING_2_1
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

private fun kotlinJvmConsumerBuildScript(row: Map<String, String>): String =
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
    """.trimIndent()

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
