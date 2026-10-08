package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * With `setupDependencies`, a JS tool version in the consumer's catalog (`js-mocha`) above KGP's
 * own must reach the test `package.json`. Only the file is generated, so the version needn't
 * exist on npm. A newer Mocha major than KGP's own breaks Kotlin's test reporter, so it must
 * warn, also when set through Kotlin's own DSL with `setupDependencies` off (Kotlin's own Mocha
 * must not: the Playwright case forbids the warning).
 */
internal fun runKmpNpmToolVersionCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-npm-tools")
    val output = runNpmToolCase(row, tempDir, projectDir, MOCHA).output
    check(MOCHA_WARNING in output) { "A newer Mocha major must warn ($MOCHA_WARNING):\n$output" }
    val packageJson = projectDir
        .resolve("build/js/packages/compat-kmp-npm-tools-test/package.json").readText()
    check("\"mocha\": \"$MOCHA\"" in packageJson) {
        "The catalog's js-mocha $MOCHA must reach the test package.json:\n$packageJson"
    }
    val viaDsl = runNpmToolCase(row, tempDir, projectDir, "1.0.0", dslMocha = MOCHA).output
    check(MOCHA_WARNING in viaDsl) { "Mocha $MOCHA set in Kotlin's DSL must warn:\n$viaDsl" }
}

private fun runNpmToolCase(
    row: Map<String, String>,
    tempDir: Path,
    projectDir: Path,
    mocha: String,
    dslMocha: String? = null,
) = runConsumerCase(
    row,
    tempDir,
    rootProjectName = "compat-kmp-npm-tools",
    projectDir = projectDir,
    tasks = listOf("jsTestPackageJson"),
    forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
) { dir ->
    dir.resolve("build.gradle.kts").writeText(npmToolBuildScript(row, dslMocha))
    dir.resolve("settings.gradle.kts").toFile().appendText(
        """

        dependencyResolutionManagement {
            versionCatalogs {
                create("libs") {
                    version("js-mocha", "$mocha")
                }
            }
        }
        """.trimIndent(),
    )
    dir.resolve("src/commonMain/kotlin").createDirectories().resolve("S.kt")
        .writeText("package compat\n\nfun one(): Int = 1\n")
}

private const val MOCHA = "99.0.0"
internal const val MOCHA_WARNING = "is a newer major than Kotlin's own Mocha"

/** [dslMocha] set: the version comes from Kotlin's own DSL, with fluxo's catalog step off. */
private fun npmToolBuildScript(row: Map<String, String>, dslMocha: String?) =
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
            setupDependencies = ${dslMocha == null}
        },
        kmp = {
            js()
        },
    )

    kotlin {
        js { nodejs() }
    }
    """.trimIndent() + dslMocha?.let {
        """

        plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin> {
            the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension>()
                .versions.mocha.version = "$it"
        }
        """.trimIndent()
    }.orEmpty()
