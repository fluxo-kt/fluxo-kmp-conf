package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * With `setupDependencies`, a JS tool version in the consumer's catalog (`js-mocha`) above KGP's
 * own must reach the test `package.json`. Only the file is generated, so the version needn't
 * exist on npm.
 */
internal fun runKmpNpmToolVersionCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-npm-tools")
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-npm-tools",
        projectDir = projectDir,
        tasks = listOf("jsTestPackageJson"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { dir ->
        dir.resolve("build.gradle.kts").writeText(npmToolBuildScript(row))
        dir.resolve("settings.gradle.kts").toFile().appendText(
            """

            dependencyResolutionManagement {
                versionCatalogs {
                    create("libs") {
                        version("js-mocha", "$MOCHA")
                    }
                }
            }
            """.trimIndent(),
        )
        dir.resolve("src/commonMain/kotlin").createDirectories().resolve("S.kt")
            .writeText("package compat\n\nfun one(): Int = 1\n")
    }
    val packageJson = projectDir
        .resolve("build/js/packages/compat-kmp-npm-tools-test/package.json").readText()
    check("\"mocha\": \"$MOCHA\"" in packageJson) {
        "The catalog's js-mocha $MOCHA must reach the test package.json:\n$packageJson"
    }
}

private const val MOCHA = "99.0.0"

private fun npmToolBuildScript(row: Map<String, String>) =
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
        kmp = {
            js()
        },
    )

    kotlin {
        js { nodejs() }
    }
    """.trimIndent()
