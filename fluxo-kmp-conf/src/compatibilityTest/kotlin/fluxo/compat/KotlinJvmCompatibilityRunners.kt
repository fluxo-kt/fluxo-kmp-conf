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
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-consumer",
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(kotlinJvmConsumerBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }
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
