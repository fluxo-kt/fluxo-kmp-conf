package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * AGP 8 also ships the KMP-aware `com.android.kotlin.multiplatform.library` plugin, which a
 * consumer may apply itself; its target is configured with AGP 8's `androidLibrary {}` DSL.
 * Before AGP 8.8 the plugin has an older target type, which fluxo's setup can't use: there it
 * skips that setup with one warning instead of failing with `NoClassDefFoundError`.
 */
internal fun runAgp8KmpLibraryPluginCase(row: Map<String, String>, tempDir: Path, agp: String) {
    val agpRow = row + ("agpVersion" to agp)
    val (major, minor) = agp.split('.').map(String::toInt)
    val skipped = major == 8 && minor < 8
    val output = runConsumerCase(
        agpRow,
        tempDir,
        rootProjectName = "compat-agp8-kmp-library-plugin",
        projectDir = tempDir.resolve(row.getValue("id") + "-kmp-library-plugin-$agp"),
        tasks = listOf("help"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(agp8KmpLibraryPluginBuildScript(agpRow))
    }.output
    check(output.contains(SKIP_WARNING) == skipped) {
        "AGP $agp: the skip warning must appear only before AGP 8.8:\n$output"
    }
}

private const val SKIP_WARNING = "fluxo doesn't set up its Android targets"

private fun agp8KmpLibraryPluginBuildScript(row: Map<String, String>) =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}"
        id("com.android.kotlin.multiplatform.library") version "${row.getValue("agpVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    fkcSetupMultiplatform(
        config = {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
        },
        kmp = { jvm() },
    )

    kotlin {
        androidLibrary {
            namespace = "compat.agp8.kmp.library"
            compileSdk = 35
            minSdk = 24
        }
    }
    """.trimIndent()
