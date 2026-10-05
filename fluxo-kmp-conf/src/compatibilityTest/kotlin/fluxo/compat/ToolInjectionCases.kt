package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * KSP and plugin-publish need the Kotlin plugin's classes or apply helper plugins by id, so fluxo
 * can't load them itself without breaking the configuration cache; they must be on the module's
 * own build classpath before it is evaluated. A consumer that declares neither and asks for them
 * through fluxo (`setupKsp`, `fkcSetupGradlePlugin`) must get both applied, with the cache stored
 * and reused, with the fluxo settings line and without it (root build script only).
 */
internal fun runToolInjectionCase(row: Map<String, String>, tempDir: Path) {
    // The newest row only: which Kotlin the bundled KSP supports is the KSP floor row's subject.
    if (row.kgpMinor() < NEWEST_TESTED_KOTLIN) return
    for (settingsPlugin in listOf(true, false)) {
        val name = "tool-injection" + if (settingsPlugin) "" else "-root-only"
        for (run in listOf("stored", "reused")) {
            runToolInjectionBuild(row, tempDir, name, settingsPlugin, run)
        }
        runRootKspBuild(row, tempDir, settingsPlugin)
    }
}

/**
 * A single-module build that needs KSP in its root project: the settings plugin reaches it, the
 * root plugin alone can't (the root is already being evaluated), so that build stops with the
 * line to add.
 */
private fun runRootKspBuild(row: Map<String, String>, tempDir: Path, settingsPlugin: Boolean) {
    val name = "root-ksp" + if (settingsPlugin) "" else "-root-only"
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-$name",
        projectDir = tempDir.resolve(row.getValue("id") + "-$name"),
        tasks = listOf("help"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        expectFailure = when {
            settingsPlugin -> emptyList()
            else -> listOf(KSP_LINE_START, "the root build script")
        },
    ) { projectDir ->
        projectDir.resolve("settings.gradle.kts")
            .writeText(markerSettingsScript("compat-$name", settingsPlugin))
        projectDir.resolve("build.gradle.kts").writeText(
            markerKotlinJvmBuildScript(row)
                .replace(SETUP_LAST_LINE, "$SETUP_LAST_LINE\n    setupKsp = true") +
                "\nprintln(\"root ksp=\" + plugins.hasPlugin(\"com.google.devtools.ksp\"))\n",
        )
        writeKotlinJvmSources(projectDir)
    }.output
    check(!settingsPlugin || "root ksp=true" in output) { "$name: KSP not applied:\n$output" }
}

private fun runToolInjectionBuild(
    row: Map<String, String>,
    tempDir: Path,
    name: String,
    settingsPlugin: Boolean,
    run: String,
) {
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-$name",
        projectDir = tempDir.resolve(row.getValue("id") + "-$name"),
        tasks = listOf("help"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript("compat-$name", settingsPlugin) +
                "\ninclude(\":app\", \":gp\")\n",
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerKotlinJvmBuildScript(row) + "\n" + APPLIED_PROBE,
        )
        writeModule(projectDir.resolve("app"), "fkcSetupKotlin { $FLAGS\n    setupKsp = true\n}")
        writeModule(
            projectDir.resolve("gp"),
            "fkcSetupGradlePlugin(pluginName = \"compat-plugin\", " +
                "pluginClass = \"compat.CompatPlugin\") { $FLAGS\n" +
                "    enableApiValidation = false\n}",
        )
    }.output
    check((run == "reused") == (CONFIGURATION_CACHE_REUSED in output)) {
        "$name: configuration cache must be $run:\n$output"
    }
    if (run == "stored") {
        check(":app ksp=true" in output && ":gp publish=true" in output) {
            "$name: KSP or plugin-publish not applied:\n$output"
        }
    }
}

private fun writeModule(dir: Path, setup: String) {
    val sources = dir.resolve("src/main/kotlin/compat")
    Files.createDirectories(sources)
    dir.resolve("build.gradle.kts").writeText("group = \"compat\"\nversion = \"1.0.0\"\n$setup\n")
    sources.resolve("CompatPlugin.kt").writeText(
        "package compat\n\nimport org.gradle.api.Plugin\nimport org.gradle.api.Project\n\n" +
            "class CompatPlugin : Plugin<Project> {\n" +
            "    override fun apply(target: Project) = Unit\n}\n",
    )
}

private const val KSP_LINE_START = "Add `id(\"com.google.devtools.ksp\") version \""
private const val SETUP_LAST_LINE = "setupCoroutines = false"

private const val FLAGS = "\n    setupVerification = false\n    enablePublication = false\n" +
    "    enableGradleDoctor = false\n    setupCoroutines = false"

/** Printed while configuring, so only the run that stores the cache entry has it. */
private const val APPLIED_PROBE = """
gradle.projectsEvaluated {
    println(":app ksp=" + project(":app").plugins.hasPlugin("com.google.devtools.ksp"))
    println(":gp publish=" + project(":gp").plugins.hasPlugin("com.gradle.plugin-publish"))
}
"""

@Suppress("MagicNumber")
private val NEWEST_TESTED_KOTLIN = KotlinVersion(2, 4)
