package fluxo.compat

import java.io.File
import java.nio.file.Path
import java.util.Properties
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
    pluginClasspath: List<File> = emptyList(),
): GradleRunner {
    pinInnerJdk(projectDir, row.compatJdkMajor())
    val runner = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(gradleUserHome.toFile())
        .withGradleVersion(row.getValue("gradleVersion"))
        .withEnvironment(sanitizedEnvironment())
        .withArguments(arguments)
        .forwardOutput()
    if (pluginClasspath.isNotEmpty()) runner.withPluginClasspath(pluginClasspath)
    return runner
}

/**
 * Falsifies the [compatRunner]/[resolveCompatJdkHome] pin end-to-end: the inner build must report
 * the declared JDK. Fails closed if the pin regresses — on *any* ambient daemon JDK, since the
 * declared value (17/21) differs from the CI daemon (21/23) — so a future "drop the JDK pin"
 * regression cannot pass silently.
 */
internal fun BuildResult.assertInnerJdk(row: Map<String, String>) {
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
    pluginClasspath: List<File> = emptyList(),
) {
    if (CHECK_TASK !in row.getValue("requiredTasks").split(' ')) {
        return
    }

    val args = gradleArguments(listOf(DEPENDENCY_GUARD_BASELINE_TASK)) + extraArguments
    val result = compatRunner(row, projectDir, gradleUserHome, args, pluginClasspath).build()
    result.assertInnerJdk(row)
    assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    result.assertTaskSuccess(":$DEPENDENCY_GUARD_BASELINE_TASK")
}

internal fun sanitizedEnvironment(): Map<String, String> =
    System.getenv().filterKeys { it !in KMP_TARGET_ENV_KEYS }

internal fun Map<String, String>.isExecutionFixture(): Boolean =
    getValue("fixture").endsWith("-exec")

internal fun pluginUnderTestClasspath(): List<File> {
    val metadata = Properties()
    KotlinJvmCompatibilityTestKitSmokeTest::class.java.classLoader
        .getResourceAsStream("plugin-under-test-metadata.properties")
        .use { stream ->
            checkNotNull(stream) { "plugin-under-test-metadata.properties not found" }
            metadata.load(stream)
        }
    val implementationClasspath = metadata.getProperty("implementation-classpath")
        .split(File.pathSeparator)
        .map(::File)
    val kotlinPluginClasspath = System
        .getProperty("fluxo.compat.kotlinPluginClasspath")
        .split(File.pathSeparator)
        .filter(String::isNotBlank)
        .map(::File)
    return implementationClasspath + kotlinPluginClasspath
}

internal fun String.containsAny(needles: Iterable<String>): Boolean =
    needles.any { it in this }

internal fun BuildResult.assertTaskSuccess(path: String) {
    require(task(path)?.outcome == TaskOutcome.SUCCESS) {
        output
    }
}

internal val KNOWN_CRASH_SIGNATURES = listOf(
    "NoSuchMethodError",
    "ClassCastException",
    "NoClassDefFoundError",
    "Could not initialize class",
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
