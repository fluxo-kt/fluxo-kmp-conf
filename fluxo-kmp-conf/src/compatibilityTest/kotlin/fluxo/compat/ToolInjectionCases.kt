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
    if (row.kgpMinor() < KotlinVersion(2, 2)) {
        for (declared in listOf(false, true)) runKspOnKotlin21(row, tempDir, declared)
    }
    // The newest row only: the floor row covers the KSP-versus-Kotlin case above.
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

/**
 * Every KSP that fluxo provides (2.3+) needs Kotlin 2.2+; on Kotlin 2.1 `kspKotlin` died mid-build
 * with `NoSuchMethodError`. Without a declared KSP the build must stop at configuration with the
 * fix; with the Kotlin-tied release declared it must run KSP (the check must not over-fire).
 */
private fun runKspOnKotlin21(row: Map<String, String>, tempDir: Path, declared: Boolean) {
    val name = "ksp-kotlin21" + if (declared) "-declared" else ""
    val kotlin = row.getValue("kgpVersion")
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-$name",
        projectDir = tempDir.resolve(row.getValue("id") + "-$name"),
        tasks = listOf("kspKotlin"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
        expectFailure = if (declared) emptyList() else listOf("needs Kotlin 2.2 or newer"),
        assertTasksSucceed = declared,
    ) { projectDir ->
        val script = markerKotlinJvmBuildScript(row)
            .replace(SETUP_LAST_LINE, "$SETUP_LAST_LINE\n    setupKsp = true")
        projectDir.resolve("build.gradle.kts").writeText(
            if (!declared) {
                script
            } else {
                // A real processor: without one `kspKotlin` is SKIPPED and never reaches the
                // call that broke.
                script.replace(
                    "plugins {",
                    "plugins {\n    id(\"com.google.devtools.ksp\") version \"$kotlin-2.0.2\"",
                ) + "\ndependencies {\n" +
                    "    implementation(\"com.squareup.moshi:moshi:1.15.2\")\n" +
                    "    ksp(\"com.squareup.moshi:moshi-kotlin-codegen:1.15.2\")\n}\n"
            },
        )
        writeKotlinJvmSources(projectDir)
        projectDir.resolve("src/main/kotlin/compat/P.kt").writeText(
            "package compat\n\n@com.squareup.moshi.JsonClass(generateAdapter = true)\n" +
                "class P(val name: String)\n",
        )
    }
    if (declared) {
        val adapter = tempDir.resolve(row.getValue("id") + "-$name")
            .resolve("build/generated/ksp/main/kotlin/compat/PJsonAdapter.kt")
        check(Files.exists(adapter)) { "$name: KSP generated no adapter at $adapter" }
    }
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
                "\ninclude(\":app\", \":gp\", \":nested:leaf\", \":empty\")\n",
        )
        // A tool on a parent's classpath (`:nested`) would fail this versioned declaration with
        // "already on the classpath with an unknown version". `:empty` has no build file, so no
        // repositories of its own; the tools injected there must still resolve.
        Files.createDirectories(projectDir.resolve("nested/leaf"))
        projectDir.resolve("nested/leaf/build.gradle.kts").writeText(
            "plugins { id(\"com.gradle.plugin-publish\") version \"2.2.1\" apply false }\n",
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
internal val NEWEST_TESTED_KOTLIN = KotlinVersion(2, 4)
