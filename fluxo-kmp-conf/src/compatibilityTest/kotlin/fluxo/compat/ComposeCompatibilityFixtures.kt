package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

internal fun markerComposeDesktopBuildScript(row: Map<String, String>): String =
    COMPOSE_DESKTOP_BUILD_SCRIPT
        .replace("@COMPOSE_COMPILER_PLUGIN@", composeDesktopCompilerPlugin(row))
        .replace("@KGP_VERSION@", row.getValue("kgpVersion"))
        .replace("@COMPOSE_VERSION@", row.getValue("composeVersion"))
        .replace("@PLUGIN_ID@", pluginId())
        .replace("@PLUGIN_VERSION@", pluginVersion())

internal fun markerComposeKmpAndroidBuildScript(row: Map<String, String>): String {
    val isAgp9 = row.getValue("fixture") == "compose-kmp-agp9"
    return COMPOSE_KMP_ANDROID_BUILD_SCRIPT
        .replace("@KGP_VERSION@", row.getValue("kgpVersion"))
        .replace("@COMPOSE_VERSION@", row.getValue("composeVersion"))
        .replace("@AGP_VERSION@", row.getValue("agpVersion"))
        .replace("@PLUGIN_ID@", pluginId())
        .replace("@PLUGIN_VERSION@", pluginVersion())
        .replace("@ANDROID_PLUGIN@", composeKmpAndroidPlugin(isAgp9))
        .replace("@ANDROID_NAMESPACE@", "compat.compose.${if (isAgp9) "agp9" else "agp8"}.kmp")
        .replace("@SHAPE_TASK@", composeKmpShapeTask(isAgp9))
        .replace("@IS_AGP9@", isAgp9.toString())
        .replace("@IS_AGP8@", (!isAgp9).toString())
        .replace("@ANDROID_TARGET_ASSERTION@", composeKmpAndroidTargetAssertion(isAgp9))
}

private fun composeDesktopCompilerPlugin(row: Map<String, String>): String {
    val version = row.getValue("kgpVersion")
    val plugin = "id(\"org.jetbrains.kotlin.plugin.compose\") version \"$version\""
    return if (row.getValue("fixture") == "compose-desktop-preapplied") {
        plugin
    } else {
        "$plugin apply false"
    }
}

private fun composeKmpAndroidPlugin(isAgp9: Boolean): String =
    if (isAgp9) "com.android.kotlin.multiplatform.library" else "com.android.library"

private fun composeKmpShapeTask(isAgp9: Boolean): String =
    if (isAgp9) "assertAgp9KmpComposeShape" else "assertAgp8KmpComposeShape"

private fun composeKmpAndroidTargetAssertion(isAgp9: Boolean): String =
    if (isAgp9) {
        """
        val androidTarget = androidTargets.single()
            as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
        check(androidTarget.namespace == "compat.compose.agp9.kmp")
        check(androidTarget.compileSdk == 35)
        check(androidTarget.minSdk == 24)
        """.trimIndent()
    } else {
        """
        check(
            androidTargets.single() is
                org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget,
        )
        val android = project.extensions.getByName("android")
            as com.android.build.api.dsl.LibraryExtension
        check(android.namespace == "compat.compose.agp8.kmp")
        check(android.compileSdk == 35)
        check(android.defaultConfig.minSdk == 24)
        """.trimIndent()
    }

private val COMPOSE_DESKTOP_BUILD_SCRIPT =
    """
    import org.jetbrains.compose.desktop.application.dsl.TargetFormat

    plugins {
        id("org.jetbrains.kotlin.jvm") version "@KGP_VERSION@"
        @COMPOSE_COMPILER_PLUGIN@
        id("org.jetbrains.compose") version "@COMPOSE_VERSION@"
        id("@PLUGIN_ID@") version "@PLUGIN_VERSION@"
    }

    group = "compat"
    version = "1.0.0"

    val mainClassName = "compat.MainKt"

    fun composeDesktopCurrentOs(): String {
        val arch = when (val value = System.getProperty("os.arch")) {
            "x86_64", "amd64" -> "x64"
            "aarch64" -> "arm64"
            else -> error("Unsupported OS arch: ${'$'}value")
        }
        val platform = when (val os = System.getProperty("os.name")) {
            "Mac OS X" -> "macos"
            else -> when {
                os.startsWith("Win", ignoreCase = true) -> "windows"
                os.startsWith("Linux", ignoreCase = true) -> "linux"
                else -> error("Unknown OS name: ${'$'}os")
            }
        }
        return "org.jetbrains.compose.desktop:desktop-jvm-${'$'}platform-${'$'}arch:@COMPOSE_VERSION@"
    }

    fkcSetupKotlinApp {
        setupVerification = false
        enablePublication = false
        enableGradleDoctor = false
        setupCoroutines = false
    }

    dependencies {
        implementation(composeDesktopCurrentOs())
        implementation("org.jetbrains.compose.components:components-resources:@COMPOSE_VERSION@")
        implementation("org.jetbrains.compose.ui:ui-tooling-preview:@COMPOSE_VERSION@")
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:@KGP_VERSION@")
    }

    compose.desktop.application {
        mainClass = mainClassName
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageVersion = "1.0.0"
            packageName = "CompatCompose"
        }
    }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
    }

    tasks.register("assertComposeDesktopShape") {
        doLast {
            check(plugins.hasPlugin("org.jetbrains.kotlin.jvm"))
            check(plugins.hasPlugin("org.jetbrains.compose"))
            check(plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose"))
            check(tasks.names.contains("createReleaseDistributable"))
        }
    }
    """.trimIndent()

private val COMPOSE_KMP_ANDROID_BUILD_SCRIPT =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "@KGP_VERSION@" apply false
        id("org.jetbrains.kotlin.plugin.compose") version "@KGP_VERSION@" apply false
        id("org.jetbrains.compose") version "@COMPOSE_VERSION@"
        id("@ANDROID_PLUGIN@") version "@AGP_VERSION@" apply false
        id("@PLUGIN_ID@") version "@PLUGIN_VERSION@"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupMultiplatform(
        config = {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            androidNamespace = "@ANDROID_NAMESPACE@"
            androidCompileSdk = 35
            androidMinSdk = 24
        },
        kmp = { allDefaultTargets() },
    )

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        dependencies.add(
            "commonMainImplementation",
            "org.jetbrains.compose.runtime:runtime:@COMPOSE_VERSION@",
        )
    }

    tasks.register("@SHAPE_TASK@") {
        doLast {
            check(plugins.hasPlugin("org.jetbrains.compose"))
            check(plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose"))
            check(plugins.hasPlugin("@ANDROID_PLUGIN@"))
            check(plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"))
            check(plugins.hasPlugin("com.android.library") == @IS_AGP8@)
            check(plugins.hasPlugin("com.android.kotlin.multiplatform.library") == @IS_AGP9@)

            val kotlin = project.extensions.getByType(
                org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java,
            )
            val androidTargets = kotlin.targets.matching { it.name == "android" }
            check(androidTargets.size == 1) {
                "Expected one Android KMP target, got ${'$'}androidTargets"
            }
            @ANDROID_TARGET_ASSERTION@
            check(kotlin.targets.names.contains("jvm"))
        }
    }
    """.trimIndent()

internal fun writeComposeKmpSources(projectDir: Path) {
    writeAndroidLintConfig(projectDir)
    val commonMainDir = projectDir.resolve("src/commonMain/kotlin/compat")
    val jvmMainDir = projectDir.resolve("src/jvmMain/kotlin/compat")
    val androidMainDir = projectDir.resolve("src/androidMain/kotlin/compat")
    Files.createDirectories(commonMainDir)
    Files.createDirectories(jvmMainDir)
    Files.createDirectories(androidMainDir)
    commonMainDir.resolve("SharedComposable.kt").writeText(
        """
        package compat

        import androidx.compose.runtime.Composable

        @Composable
        fun SharedComposableLabel(): String = "Fluxo"
        """.trimIndent(),
    )
    jvmMainDir.resolve("JvmComposeEntry.kt").writeText(
        """
        package compat

        import androidx.compose.runtime.Composable

        @Composable
        fun JvmComposableLabel(): String = SharedComposableLabel()
        """.trimIndent(),
    )
    androidMainDir.resolve("AndroidComposeEntry.kt").writeText(
        """
        package compat

        import androidx.compose.runtime.Composable

        @Composable
        fun AndroidComposableLabel(): String = SharedComposableLabel()
        """.trimIndent(),
    )
}

internal fun writeComposeDesktopSources(projectDir: Path) {
    val mainDir = projectDir.resolve("src/main/kotlin/compat")
    val testDir = projectDir.resolve("src/test/kotlin/compat")
    Files.createDirectories(mainDir)
    Files.createDirectories(testDir)
    mainDir.resolve("Main.kt").writeText(
        """
        package compat

        import androidx.compose.material.Button
        import androidx.compose.material.MaterialTheme
        import androidx.compose.material.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.window.Window
        import androidx.compose.ui.window.application
        import androidx.compose.ui.tooling.preview.Preview

        internal fun buttonLabel(raw: String): String =
            raw.trim().replaceFirstChar { it.uppercase() }

        @Composable
        @Preview
        internal fun App() {
            MaterialTheme {
                Button(onClick = {}) {
                    Text(buttonLabel(" compat"))
                }
            }
        }

        fun main() = application {
            Window(onCloseRequest = ::exitApplication) {
                App()
            }
        }
        """.trimIndent(),
    )
    testDir.resolve("MainTest.kt").writeText(
        """
        package compat

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class MainTest {
            @Test
            fun normalizesButtonLabel() {
                assertEquals("Compat", buttonLabel(" compat"))
            }
        }
        """.trimIndent(),
    )
}
