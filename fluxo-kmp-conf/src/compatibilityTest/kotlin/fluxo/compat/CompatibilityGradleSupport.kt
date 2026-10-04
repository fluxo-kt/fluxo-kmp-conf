package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse

internal fun markerSettingsScript(rootProjectName: String): String {
    val localMavenRepo = localMavenRepoPath()
    return """
        pluginManagement {
            repositories {
                maven("$localMavenRepo")
                google()
                gradlePluginPortal()
                mavenCentral()
            }
        }

        dependencyResolutionManagement {
            repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            repositories {
                google()
                mavenCentral()
                gradlePluginPortal()
            }
        }

        rootProject.name = "$rootProjectName"

        // Reported at settings-evaluation so every fixture (incl. buildAndFail rows, which fail
        // later during configuration) emits it; `assertInnerJdk` cross-checks it against the row's
        // declared `jdkVersion`, falsifying the inner-JDK pin end-to-end.
        println("$INNER_JDK_MARKER" + System.getProperty("java.specification.version"))
    """.trimIndent()
}

internal fun gradleArguments(requiredTasks: List<String>): List<String> =
    requiredTasks + "--stacktrace"

internal const val INNER_JDK_MARKER = "FLUXO_COMPAT_INNER_JDK="

internal const val CONFIGURATION_CACHE_REUSED = "Reusing configuration cache."

/**
 * Single construction point for every fixture's [GradleRunner] — the *one* place the inner-build
 * JDK is pinned to the row's declared `jdkVersion` via [pinInnerJdk]. Centralised so no runner can
 * silently skip the pin; callers append `.build()` / `.buildAndFail()` and then [assertInnerJdk].
 *
 * Without the pin, fixtures inherit the ambient daemon JDK: a consumer's `compileXxx` then emits
 * bytecode whose major version tracks that JDK, and a bytecode-reading task (BCV `androidApiBuild`,
 * ASM-based) throws `Unsupported class file major version N` once the daemon JDK outruns the
 * wrapped tool's ASM ceiling. That asymmetry — every *other* axis (Gradle/KGP/AGP) is enforced,
 * only `jdkVersion` was dead metadata — let the defect pass dev CI (JDK 21) yet fail release
 * (JDK 23) on the same commit.
 */
internal fun compatRunner(
    row: Map<String, String>,
    projectDir: Path,
    gradleUserHome: Path,
    arguments: List<String>,
): GradleRunner {
    pinInnerJdk(projectDir, row.compatJdkMajor())
    return GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(gradleUserHome.toFile())
        .withGradleVersion(row.getValue("gradleVersion"))
        .withEnvironment(sanitizedEnvironment() + TRIPWIRE_ENVIRONMENT)
        .withArguments(arguments + tripwireArguments(projectDir, row))
        .forwardOutput()
}

/**
 * Runs one consumer build for [row] and applies the checks every case needs, so a case states
 * only its own project and expectations.
 *
 * [writeProject] fills [projectDir]; a `settings.gradle.kts` is written first from
 * [rootProjectName] and may be overwritten. [tasks] (by default the row's `requiredTasks`) run
 * with [arguments] appended, after a dependency-guard baseline is seeded when they include
 * `check` and [seedBaseline] is on (off where the build has no dependency-guard, as under
 * `DISABLE_TESTS`). With [expectFailure] empty the build must pass, print none of
 * [forbiddenOutput] and, when [assertTasksSucceed], succeed every task in [tasks]; otherwise it
 * must fail and print every [expectFailure] text. Returns the result for case-specific checks.
 * Several cases may share one [projectDir] to reuse its configuration-cache entry.
 */
@Suppress("LongParameterList")
internal fun runConsumerCase(
    row: Map<String, String>,
    tempDir: Path,
    rootProjectName: String,
    projectDir: Path = tempDir.resolve(row.getValue("id")),
    tasks: List<String> = row.getValue("requiredTasks").split(' '),
    arguments: List<String> = emptyList(),
    forbiddenOutput: List<String> = emptyList(),
    expectFailure: List<String> = emptyList(),
    assertTasksSucceed: Boolean = true,
    seedBaseline: Boolean = true,
    writeProject: (Path) -> Unit,
): BuildResult {
    // `status` is the row's claim; the runner's expectation is what actually gets checked.
    val unsupported = row.getValue("status") == "unsupported"
    check(unsupported == expectFailure.isNotEmpty()) {
        "Row ${row.getValue("id")}: status '${row.getValue("status")}' in compat/matrix.tsv " +
            "contradicts its runner, which expects the build to " +
            if (unsupported) "pass" else "fail"
    }
    Files.createDirectories(projectDir)
    projectDir.resolve("settings.gradle.kts").writeText(markerSettingsScript(rootProjectName))
    writeProject(projectDir)
    val gradleUserHome = compatGradleUserHome()
    Files.createDirectories(gradleUserHome)
    val args = gradleArguments(tasks) + arguments
    val runner = compatRunner(row, projectDir, gradleUserHome, args)

    val result = if (expectFailure.isEmpty()) {
        if (seedBaseline && CHECK_TASK in tasks) {
            seedDependencyGuardBaseline(row, projectDir, gradleUserHome, arguments)
        }
        runner.build()
    } else {
        runner.buildAndFail()
    }
    result.assertInnerJdk(row)
    result.assertNoOwnDeprecations()
    assertFalse(result.output.containsAny(FORBIDDEN_OUTPUT_SIGNATURES), result.output)
    if (expectFailure.isNotEmpty()) {
        expectFailure.forEach {
            check(it in result.output) { "Expected '$it' in:\n${result.output}" }
        }
        return result
    }
    val noise = forbiddenOutput + PUBLICATION_NOISE_SIGNATURES + DEPENDENCY_GUARD_BASELINE_NOISE
    assertFalse(result.output.containsAny(noise), result.output)
    if (assertTasksSucceed) {
        tasks.forEach { result.assertTaskSuccess(":$it") }
    }
    return result
}

/**
 * Falsifies the [compatRunner]/[resolveCompatJdkHome] pin end-to-end: the inner build must report
 * the declared JDK. Fails closed if the pin regresses — on *any* ambient daemon JDK, since the
 * declared value (17/21) differs from the CI daemon (21/23) — so a future "drop the JDK pin"
 * regression cannot pass silently.
 */
internal fun BuildResult.assertInnerJdk(row: Map<String, String>) {
    // A configuration-cache hit runs no settings script, so it cannot print the marker. The run
    // that stored the entry was checked, and a different `org.gradle.java.home` invalidates it.
    if (CONFIGURATION_CACHE_REUSED in output) return
    val major = row.getValue("jdkVersion")
    check("$INNER_JDK_MARKER$major" in output) {
        "Inner build did not run on declared jdkVersion=$major (compat/matrix.tsv); " +
            "org.gradle.java.home pin regressed?\n$output"
    }
}

internal fun seedDependencyGuardBaseline(
    row: Map<String, String>,
    projectDir: Path,
    gradleUserHome: Path,
    extraArguments: List<String> = emptyList(),
) {
    val args = gradleArguments(listOf(DEPENDENCY_GUARD_BASELINE_TASK)) + extraArguments
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()
    result.assertInnerJdk(row)
    result.assertNoOwnDeprecations()
    assertFalse(result.output.containsAny(FORBIDDEN_OUTPUT_SIGNATURES), result.output)
    result.assertTaskSuccess(":$DEPENDENCY_GUARD_BASELINE_TASK")
}

internal fun sanitizedEnvironment(): Map<String, String> =
    System.getenv().filterKeys { it !in KMP_TARGET_ENV_KEYS }

internal fun Map<String, String>.isExecutionFixture(): Boolean =
    getValue("fixture").endsWith("-exec")

internal fun String.containsAny(needles: Iterable<String>): Boolean =
    needles.any { it in this }

internal fun BuildResult.assertTaskSuccess(path: String) {
    require(task(path)?.outcome == TaskOutcome.SUCCESS) {
        output
    }
}

internal val FORBIDDEN_OUTPUT_SIGNATURES = listOf(
    "NoSuchMethodError",
    "ClassCastException",
    "NoClassDefFoundError",
    "Could not initialize class",
    // AGP's obsolete API/DSL/configuration warnings ("API 'X' is obsolete…", "DSL element 'X' is
    // obsolete…", `DeprecationReporterImpl`) only print, so this check is what makes them fatal.
    "' is obsolete",
)

internal val PUBLICATION_NOISE_SIGNATURES = listOf(
    "SIGNING_KEY",
    "Publications are unsigned",
    "setup maven POM",
    "maven publication",
)

internal val DEPENDENCY_GUARD_BASELINE_NOISE = listOf(
    "Dependency Guard baseline created",
)

internal const val CHECK_TASK = "check"

internal const val DEPENDENCY_GUARD_BASELINE_TASK = "dependencyGuardBaseline"

internal val KMP_NO_TARGET_DIAGNOSTICS = listOf(
    "no applicable Kotlin targets found",
    "No Kotlin Targets Declared",
    "Unused Kotlin Source Sets",
)

internal val ANDROID_LINT_VERSION_NOISE = listOf(
    "A newer version of com.android.library",
    "A newer version of org.jetbrains.kotlin",
    "AndroidGradlePluginVersion",
    "NewerVersionAvailable",
)

internal val DETEKT_CLASSIFICATION_NOISE = listOf(
    "Unexpected Detekt task",
    "platform UNKNOWN is disabled",
)

internal val FORBIDDEN_RUNTIME_LEAKS = listOf(
    "kotlin-compiler-embeddable",
    "detekt-core",
)

internal val KMP_TARGET_ENV_KEYS = setOf(
    "KMP_TARGETS",
    "KMP_TARGETS_ALL",
)
