package fluxo.compat

import java.io.DataInputStream
import java.nio.file.Path
import kotlin.io.path.inputStream
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

    runDeprecatedLanguageVersionCase(row, tempDir)
    runJvmTarget26Case(row, tempDir)
    runDefaultJvmTargetCase(row, tempDir)
}

/**
 * Runs `compileKotlin` on a separate copy of the Kotlin/JVM fixture with extra [setup] lines, so a
 * case that changes compiler settings never invalidates the main fixture's configuration cache.
 */
private fun runKotlinJvmVariant(
    row: Map<String, String>,
    tempDir: Path,
    name: String,
    vararg setup: String,
    jdk: Int? = null,
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
        tasks = listOf("compileKotlin"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        expectFailure = expectFailure,
    ) {
        it.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row, *setup))
        writeKotlinJvmSources(it)
    }.output
    return projectDir to output
}

private fun Map<String, String>.kgpMinor(): KotlinVersion {
    val (major, minor) = getValue("kgpVersion").split('.').map(String::toInt)
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

/**
 * The highest JVM target is whatever the consumer's Kotlin supports, never a table inside the
 * plugin: Kotlin 2.4 added target 26, so there 26 must reach the class files unchanged, and an
 * older Kotlin must reject an explicit 26 with the fix instead of quietly compiling to a lower
 * target. `useJdkRelease = false` keeps the JDK API limit out of this case: the fixture JDK
 * (17) has no API description for 26, which is a separate decision.
 */
private fun runJvmTarget26Case(row: Map<String, String>, tempDir: Path) {
    val supported = row.kgpMinor() >= FIRST_KOTLIN_WITH_JVM_26
    val (projectDir, output) = runKotlinJvmVariant(
        row,
        tempDir,
        "jvm26",
        "jvmTarget = \"26\"",
        "useJdkRelease = false",
        expectFailure = if (supported) emptyList() else listOf(JVM_TARGET_ABOVE_MAX),
    )
    if (!supported) return
    val major = projectDir.mainClassMajor()
    check(major == JAVA_26_CLASS_MAJOR) { "jvmTarget 26 compiled to major $major:\n$output" }
}

/**
 * A library that sets no JVM target must compile to 17 whatever JDK runs the build: a target that
 * followed the build JDK made bytecode depend on the machine (it sank v0.15.0's first tag). The
 * fixture rows run on JDK 17, where both behaviours agree, so this case runs on JDK 21.
 */
private fun runDefaultJvmTargetCase(row: Map<String, String>, tempDir: Path) {
    val (projectDir, output) = runKotlinJvmVariant(row, tempDir, "jdk21", jdk = JDK_21)
    val major = projectDir.mainClassMajor()
    check(major == JAVA_17_CLASS_MAJOR) { "Default library target compiled to $major:\n$output" }
}

/** A class file starts with its magic number, minor version, then major version. */
private fun Path.mainClassMajor(): Int =
    DataInputStream(resolve(MAIN_CLASS).inputStream()).use {
        it.readInt()
        it.readUnsignedShort()
        it.readUnsignedShort()
    }

private const val MAIN_CLASS = "build/classes/kotlin/main/compat/CompatSubjectKt.class"

private const val JDK_21 = 21

private const val JAVA_17_CLASS_MAJOR = 61

private const val DEPRECATED_VERSION_WARNING = "Kotlin language/API version 2.1 is deprecated"

private const val JVM_TARGET_ABOVE_MAX = "supports JVM targets up to"

private const val JAVA_26_CLASS_MAJOR = 70

/** The first Kotlin release that deprecates language version 2.1. */
@Suppress("MagicNumber")
private val FIRST_KOTLIN_DEPRECATING_2_1 = KotlinVersion(2, 4)

/** The first Kotlin release whose `JvmTarget` has 26 (kotlin-compiler 2.4.10 `JvmTarget.kt`). */
@Suppress("MagicNumber")
private val FIRST_KOTLIN_WITH_JVM_26 = KotlinVersion(2, 4)

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
