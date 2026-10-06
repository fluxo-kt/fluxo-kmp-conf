package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * A JS target makes fluxo configure Kotlin's Yarn and Node.js root extensions. Their extension
 * names are `const val`s on Kotlin 2.1 and companion getters from 2.2, so a lookup compiled
 * against a newer Kotlin fails on 2.1 unless it is read by value. Runs on every KMP row: the
 * other KMP cases filter JS out on the floor row.
 */
internal fun runKmpJsCase(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-js",
        projectDir = tempDir.resolve("${row.getValue("id")}-js"),
        tasks = listOf("help"),
        arguments = listOf("-PKMP_TARGETS=JS"),
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
                    setupCoroutines = false
                    setupDependencies = true
                },
                kmp = { js() },
            )
            """.trimIndent(),
        )
    }
}
