package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * Publication enabled but fluxo's setup can't run: no project version, or the Vanniktech plugin
 * not declared. A publishing build must fail with that cause, not run on with an error line and
 * stop later at a missing task; a build that doesn't publish keeps passing, as it did before.
 */
internal fun runPublicationSetupFailureCase(row: Map<String, String>, tempDir: Path) {
    val scenarios = mapOf(
        "no-version" to "Publication artifact version is not set!",
        "no-vanniktech" to "Can't load plugin 'com.vanniktech.maven.publish'",
    )
    for ((scenario, cause) in scenarios) {
        fun case(tasks: List<String>, expectFailure: List<String>) = runConsumerCase(
            row,
            tempDir,
            rootProjectName = "compat-kotlin-jvm-publication",
            projectDir = tempDir.resolve(row.getValue("id") + "-publication-$scenario"),
            tasks = tasks,
            expectFailure = expectFailure,
            forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
            assertTasksSucceed = expectFailure.isEmpty(),
        ) { projectDir ->
            val script = publicationBuildScript(row, plainGradle = scenario == "no-version")
            projectDir.resolve("build.gradle.kts").writeText(script)
            writeKotlinJvmSources(projectDir)
        }.output

        val output = case(listOf("publishToMavenLocal"), expectFailure = listOf(cause))
        val whatWentWrong = output.substringAfter("* What went wrong:").substringBefore("* Try:")
        check(cause in whatWentWrong) { "$scenario: the build must fail with:\n$output" }
        case(listOf("help"), expectFailure = emptyList())
    }
}

/**
 * [plainGradle]: Gradle's own publishing and no project version. Otherwise the default Vanniktech
 * path with a version, but the Vanniktech plugin left undeclared.
 */
private fun publicationBuildScript(row: Map<String, String>, plainGradle: Boolean) =
    """
    plugins {
        id("org.jetbrains.kotlin.jvm") version "${row.getValue("kgpVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    ${if (plainGradle) "" else "version = \"1.0.0\""}

    fkcSetupKotlin {
        setupVerification = false
        enablePublication = true
        ${if (plainGradle) "useVanniktechPublish = false" else ""}
        enableGradleDoctor = false
        setupCoroutines = false
        publicationConfig()
    }
    """.trimIndent()
