package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse

/**
 * The consumer shape where the Kotlin plugin sits on the root `buildscript` classpath and only
 * fluxo is in `plugins {}`. Fluxo resolves from the local Maven repository like any consumer's,
 * so the row's `kgpVersion` is the Kotlin plugin it runs against.
 */
internal fun runKotlinJvmConsumer(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id"))
    Files.createDirectories(projectDir)
    writeKotlinJvmConsumerProject(projectDir, row)
    val gradleUserHome = compatGradleUserHome()
    Files.createDirectories(gradleUserHome)
    val requiredTasks = row.getValue("requiredTasks").split(' ')
    seedDependencyGuardBaseline(row, projectDir, gradleUserHome)

    val args = gradleArguments(requiredTasks)
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()

    result.assertInnerJdk(row)

    result.assertNoOwnDeprecations()
    assertFalse(result.output.containsAny(FORBIDDEN_OUTPUT_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
    requiredTasks.forEach { result.assertTaskSuccess(":$it") }
}

private fun writeKotlinJvmConsumerProject(projectDir: Path, row: Map<String, String>) {
    projectDir.resolve("settings.gradle.kts").writeText(
        markerSettingsScript(rootProjectName = "compat-kotlin-jvm-consumer"),
    )
    projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
    writeKotlinJvmSources(projectDir)
}

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
    val projectDir = tempDir.resolve(row.getValue("id"))
    Files.createDirectories(projectDir)
    projectDir.resolve("settings.gradle.kts").writeText(
        markerSettingsScript(rootProjectName = "compat-kotlin-jvm-tests-disabled-consumer"),
    )
    projectDir.resolve("build.gradle.kts").writeText(
        markerKotlinJvmBuildScript(row),
    )
    writeKotlinJvmSources(projectDir)
    val gradleUserHome = compatGradleUserHome()
    Files.createDirectories(gradleUserHome)
    val requiredTasks = row.getValue("requiredTasks").split(' ')

    val args = gradleArguments(requiredTasks) + "-PDISABLE_TESTS=true"
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()

    result.assertInnerJdk(row)

    result.assertNoOwnDeprecations()
    assertFalse(result.output.containsAny(FORBIDDEN_OUTPUT_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    check(result.task(":test")?.outcome != TaskOutcome.SUCCESS) {
        result.output
    }
    check(result.task(":compileTestKotlin")?.outcome != TaskOutcome.SUCCESS) {
        result.output
    }
    requiredTasks.forEach {
        check(result.task(":$it") != null) {
            result.output
        }
    }
}

internal fun runKotlinJvmMarkerConsumer(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve("${row.getValue("id")}-marker")
    Files.createDirectories(projectDir)
    projectDir.resolve("settings.gradle.kts").writeText(
        markerSettingsScript(rootProjectName = "compat-kotlin-jvm-marker-consumer"),
    )
    projectDir.resolve("build.gradle.kts").writeText(
        markerKotlinJvmBuildScript(row),
    )
    writeKotlinJvmSources(projectDir)
    val gradleUserHome = compatGradleUserHome()
    Files.createDirectories(gradleUserHome)
    val requiredTasks = row.getValue("requiredTasks").split(' ')
    seedDependencyGuardBaseline(row, projectDir, gradleUserHome)

    val args = gradleArguments(requiredTasks)
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()

    result.assertInnerJdk(row)

    result.assertNoOwnDeprecations()
    assertFalse(result.output.containsAny(FORBIDDEN_OUTPUT_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
    requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    assertNoForbiddenResolvedClasspathLeaks(projectDir)
}
