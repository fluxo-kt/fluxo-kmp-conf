package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Publication enabled but fluxo's setup can't run because the project has no version. A
 * publishing build must fail with that cause, not run on with an error line and stop later at a
 * missing task; a build that doesn't publish keeps passing, as it did before, with a warning that
 * names the cause and its fix.
 */
internal fun runPublicationSetupFailureCase(row: Map<String, String>, tempDir: Path) {
    val cause = "Publication of ':' has no version"
    fun case(tasks: List<String>, expectFailure: List<String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-publication",
        projectDir = tempDir.resolve(row.getValue("id") + "-publication-no-version"),
        tasks = tasks,
        expectFailure = expectFailure,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        assertTasksSucceed = expectFailure.isEmpty(),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(publicationBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }.output

    val output = case(listOf("publishToMavenLocal"), expectFailure = listOf(cause))
    val whatWentWrong = output.substringAfter("* What went wrong:").substringBefore("* Try:")
    check(cause in whatWentWrong) { "The build must fail with:\n$output" }
    // Not publishing: one warning with the cause and its fix, no error line.
    val help = case(listOf("help"), expectFailure = emptyList())
    check(help.lines().any { it.startsWith("w: ") && cause in it }) {
        "Expected a warning with '$cause':\n$help"
    }
    check(help.lines().none { it.startsWith("e: ") }) { "Error line:\n$help" }
    // The same warning reaches Gradle's Problems API, so the problems report lists it.
    val report = tempDir.resolve(row.getValue("id") + "-publication-no-version")
        .resolve("build/reports/problems/problems-report.html").toFile()
    check(report.isFile && "setup-step-skipped" in report.readText()) {
        "No 'setup-step-skipped' in $report"
    }
}

/** Gradle's own publishing and no project version. */
private fun publicationBuildScript(row: Map<String, String>) =
    """
    plugins {
        id("org.jetbrains.kotlin.jvm") version "${row.getValue("kgpVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"

    fkcSetupKotlin {
        setupVerification = false
        enablePublication = true
        useVanniktechPublish = false
        enableGradleDoctor = false
        setupCoroutines = false
        publicationConfig()
    }
    """.trimIndent()
