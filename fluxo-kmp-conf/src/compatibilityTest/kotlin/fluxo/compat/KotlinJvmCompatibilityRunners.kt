package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse

internal fun runKotlinJvmConsumer(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id"))
    Files.createDirectories(projectDir)
    writeKotlinJvmConsumerProject(projectDir, row)
    val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
    Files.createDirectories(gradleUserHome)
    val requiredTasks = row.getValue("requiredTasks").split(' ')
    val pluginClasspath = pluginUnderTestClasspath()
    seedDependencyGuardBaseline(
        row,
        projectDir,
        gradleUserHome,
        pluginClasspath = pluginClasspath,
    )

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(gradleUserHome.toFile())
        .withGradleVersion(row.getValue("gradleVersion"))
        .withPluginClasspath(pluginClasspath)
        .withEnvironment(sanitizedEnvironment())
        .withArguments(gradleArguments(requiredTasks))
        .forwardOutput()
        .build()

    assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
    requiredTasks.forEach { result.assertTaskSuccess(":$it") }
}

private fun writeKotlinJvmConsumerProject(projectDir: Path, row: Map<String, String>) {
    projectDir.resolve("settings.gradle.kts").writeText(kotlinJvmConsumerSettingsScript())
    projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
    writeKotlinJvmSources(projectDir)
}

private fun kotlinJvmConsumerSettingsScript(): String =
    """
    pluginManagement {
        repositories {
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

    rootProject.name = "compat-kotlin-jvm-consumer"
    """.trimIndent()

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
        id("${System.getProperty("fluxo.plugin.id")}")
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
    val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
    Files.createDirectories(gradleUserHome)
    val requiredTasks = row.getValue("requiredTasks").split(' ')

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(gradleUserHome.toFile())
        .withGradleVersion(row.getValue("gradleVersion"))
        .withEnvironment(sanitizedEnvironment())
        .withArguments(gradleArguments(requiredTasks) + "-PDISABLE_TESTS=true")
        .forwardOutput()
        .build()

    assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
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
    val gradleUserHome = tempDir.resolve("${row.getValue("id")}-marker-gradle-user-home")
    Files.createDirectories(gradleUserHome)
    val requiredTasks = row.getValue("requiredTasks").split(' ')
    seedDependencyGuardBaseline(row, projectDir, gradleUserHome)

    val result = GradleRunner.create()
        .withProjectDir(projectDir.toFile())
        .withTestKitDir(gradleUserHome.toFile())
        .withGradleVersion(row.getValue("gradleVersion"))
        .withEnvironment(sanitizedEnvironment())
        .withArguments(gradleArguments(requiredTasks))
        .forwardOutput()
        .build()

    assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
    requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    assertNoForbiddenResolvedClasspathLeaks(projectDir)
}
