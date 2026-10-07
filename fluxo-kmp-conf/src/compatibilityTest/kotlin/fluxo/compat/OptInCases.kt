package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * `optIns` takes a marker's short name. Both markers are ERROR-level, so the sources compile only
 * with the opt-ins applied. `ExperimentalPathApi` exists only in the JVM stdlib: passed to the JS
 * compilation, the compiler reports it unresolved. A misspelled short name fails configuration
 * with the closest valid one.
 */
internal fun runKmpShortOptInCase(row: Map<String, String>, tempDir: Path) {
    fun case(optIns: String, expectFailure: List<String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-opt-ins",
        projectDir = tempDir.resolve(row.getValue("id") + "-opt-ins"),
        tasks = listOf("compileKotlinJvm", "compileKotlinJs"),
        expectFailure = expectFailure,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(optInBuildScript(row, optIns))
        projectDir.resolve("src/commonMain/kotlin").createDirectories().resolve("U.kt").writeText(
            "package compat\n\nfun id(): String = kotlin.uuid.Uuid.random().toString()\n",
        )
        projectDir.resolve("src/jvmMain/kotlin").createDirectories().resolve("P.kt").writeText(
            "package compat\n\nimport kotlin.io.path.deleteRecursively\n\n" +
                "fun clear(dir: java.nio.file.Path) = dir.deleteRecursively()\n",
        )
    }
    case("\"ExperimentalUuidApi\", \"ExperimentalPathApi\"", expectFailure = emptyList())
    case("\"ExperimentalUuidAPI\"", expectFailure = listOf("Did you mean ExperimentalUuidApi?"))
}

private fun optInBuildScript(row: Map<String, String>, optIns: String) =
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
            optIns = listOf($optIns)
        },
        kmp = {
            jvm()
            js()
        },
    )
    """.trimIndent()

/**
 * With coroutines on a test compilation's classpath, fluxo's default setup opts it into the
 * coroutines markers: `getCancellationException` is ERROR-level `InternalCoroutinesApi`, so the
 * test sources compile only with them. fluxo reads that from the compilation's resolved graph,
 * where the module is named per platform (`…-core-jvm`, `…-core-linuxx64`), hence one JVM and one
 * native target. The other half, no coroutines and no markers, is the KMP lifecycle case: every
 * case forbids an unresolved marker.
 */
internal fun runKmpCoroutinesOptInCase(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-coroutines-opt-ins",
        projectDir = tempDir.resolve(row.getValue("id") + "-coroutines-opt-ins"),
        tasks = listOf("compileTestKotlinJvm", "compileTestKotlinLinuxX64"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
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
                },
                kmp = {
                    jvm()
                    linuxX64()
                },
            )

            kotlin.sourceSets.getByName("commonTest").dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            }
            """.trimIndent(),
        )
        projectDir.resolve("src/commonTest/kotlin").createDirectories().resolve("C.kt").writeText(
            "package compat\n\nimport kotlinx.coroutines.Job\n\n" +
                "fun cause(job: Job): Any = job.getCancellationException()\n",
        )
    }
}
