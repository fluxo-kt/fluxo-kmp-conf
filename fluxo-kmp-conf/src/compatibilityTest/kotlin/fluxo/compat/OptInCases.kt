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
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + "is unresolved",
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
