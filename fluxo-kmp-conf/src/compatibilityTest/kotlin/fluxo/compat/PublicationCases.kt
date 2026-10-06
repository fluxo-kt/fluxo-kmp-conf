package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Publication enabled without a project version: fluxo's setup refuses it. A publishing build
 * must fail with that cause, not run on with an error line and stop later at a missing task;
 * a build that doesn't publish keeps passing, as it did before.
 */
internal fun runPublicationSetupFailureCase(row: Map<String, String>, tempDir: Path) {
    fun case(tasks: List<String>, expectFailure: List<String>) = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kotlin-jvm-publication",
        projectDir = tempDir.resolve(row.getValue("id") + "-publication"),
        tasks = tasks,
        expectFailure = expectFailure,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        assertTasksSucceed = expectFailure.isEmpty(),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(publicationBuildScript(row))
        writeKotlinJvmSources(projectDir)
    }.output

    val cause = "Publication artifact version is not set!"
    val output = case(listOf("publishToMavenLocal"), expectFailure = listOf(cause))
    val whatWentWrong = output.substringAfter("* What went wrong:").substringBefore("* Try:")
    check(cause in whatWentWrong) { "The build must fail with the setup's cause:\n$output" }
    case(listOf("help"), expectFailure = emptyList())
}

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
