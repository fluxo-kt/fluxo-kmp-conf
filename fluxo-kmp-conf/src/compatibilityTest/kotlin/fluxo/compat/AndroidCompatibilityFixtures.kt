package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

internal fun markerAgp8KmpBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue(
        "kgpVersion"
    )}" apply false
        id("com.android.library") version "${row.getValue("agpVersion")}" apply false
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupMultiplatform(
        config = {
            setupVerification = ${row.isExecutionFixture()}
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            androidNamespace = "compat.agp8.kmp"
            androidCompileSdk = 35
            androidMinSdk = 24
        },
        kmp = { allDefaultTargets() },
    )

    tasks.register("assertAgp8KmpShape") {
        doLast {
            check(plugins.hasPlugin("com.android.library"))
            check(plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"))
            check(!plugins.hasPlugin("com.android.kotlin.multiplatform.library"))

            val android = project.extensions.getByName("android")
                as com.android.build.api.dsl.LibraryExtension
            check(android.namespace == "compat.agp8.kmp")
            check(android.compileSdk == 35)
            check(android.defaultConfig.minSdk == 24)

            val kotlin = project.extensions.getByType(
                org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java,
            )
            check(
                kotlin.targets.getByName("android") is
                    org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget,
            )
        }
    }
    """.trimIndent()

// BCV isn't loaded dynamically (canLoadDynamically=false), so a consumer enabling apiValidation
// must declare it on the classpath itself. Exec rows do; version is the matrix `bcvVersion` column.
// Config-only rows get "" so they keep asserting shape without applying BCV.
private fun bcvPluginDeclaration(row: Map<String, String>): String =
    if (row.isExecutionFixture()) {
        "\n        id(\"org.jetbrains.kotlinx.binary-compatibility-validator\") " +
            "version \"${row.getValue("bcvVersion")}\" apply false"
    } else {
        ""
    }

// Inherently-long build-script template literal: the `plugins {}` block, the fkcSetupMultiplatform
// config, and the `assertAgp9KmpShape` verification task read as one coherent consumer script.
// Splitting across helpers would force a reader to reassemble the script from pieces — worse than
// the length. Consistent with the codebase's @Suppress("LongMethod") on string-builder methods.
@Suppress("LongMethod")
internal fun markerAgp9KmpBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue(
        "kgpVersion"
    )}" apply false
        id("com.android.kotlin.multiplatform.library") version "${row.getValue(
        "agpVersion"
    )}" apply false${bcvPluginDeclaration(row)}
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupMultiplatform(
        config = {
            setupVerification = ${row.isExecutionFixture()}
            // Exercises the AGP-9 KMP android-main API lane (registerAndroidMainApiTasks):
            // androidApiBuild must register off the android `main` compilation. Exec-only.
            enableApiValidation = ${row.isExecutionFixture()}
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            androidNamespace = "compat.agp9.kmp"
            androidCompileSdk = 35
            androidMinSdk = 24
        },
        kmp = { allDefaultTargets() },
    )

    project.extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>(
        "kotlin",
    ) {
        targets.named("android") {
            (this as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget)
                .withHostTest {}
        }
    }

    tasks.register("assertAgp9KmpShape") {
        doLast {
            check(plugins.hasPlugin("com.android.kotlin.multiplatform.library"))
            check(plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"))
            check(!plugins.hasPlugin("com.android.library"))
            check(project.extensions.findByName("android") == null)

            val kotlin = project.extensions.getByType(
                org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java,
            )
            val androidTargets = kotlin.targets.matching { it.name == "android" }
            check(androidTargets.size == 1) {
                "Expected one AGP 9 auto-created android KMP target, got ${'$'}androidTargets"
            }
            val androidTarget = androidTargets.single()
                as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
            check(androidTarget.namespace == "compat.agp9.kmp")
            check(androidTarget.compileSdk == 35)
            check(androidTarget.minSdk == 24)
        }
    }
    """.trimIndent()

internal fun markerAgp9KmpAppUnsupportedBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue(
        "kgpVersion"
    )}" apply false
        id("com.android.application") version "${row.getValue("agpVersion")}" apply false
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupMultiplatform(
        config = {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            androidNamespace = "compat.agp9.kmp.app"
            androidApplicationId = "compat.agp9.kmp.app"
            androidCompileSdk = 35
            androidMinSdk = 24
        },
        kmp = {
            androidApp()
        },
    )
    """.trimIndent()
internal fun markerAndroidLibraryBuildScript(row: Map<String, String>): String {
    val isAgp8 = row.getValue("fixture").startsWith("android-lib-agp8")
    val kotlinAndroidPlugin = if (isAgp8) {
        "id(\"org.jetbrains.kotlin.android\") version \"${row.getValue("kgpVersion")}\""
    } else {
        "id(\"org.jetbrains.kotlin.android\") version \"${row.getValue(
            "kgpVersion"
        )}\" apply false"
    }
    return """
    plugins {
        id("com.android.library") version "${row.getValue("agpVersion")}" apply false
        $kotlinAndroidPlugin
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupAndroidLibrary(
        namespace = "compat.${row.getValue("id").replace('-', '.')}",
        config = {
            setupVerification = ${row.isExecutionFixture()}
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
            androidCompileSdk = 35
            androidMinSdk = 24
        },
    )

    tasks.register("assertAndroidLibraryShape") {
        doLast {
            check(plugins.hasPlugin("com.android.library"))
            check(!plugins.hasPlugin("com.android.kotlin.multiplatform.library"))
            check(plugins.hasPlugin("org.jetbrains.kotlin.android") == $isAgp8)

            val android = project.extensions.getByName("android")
                as com.android.build.api.dsl.LibraryExtension
            check(android.namespace == "compat.${row.getValue("id").replace('-', '.')}")
            check(android.compileSdk == 35)
            check(android.defaultConfig.minSdk == 24)

            val kotlin = project.extensions.getByName("kotlin")
            check(kotlin is org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension)
        }
    }
    """.trimIndent()
}

internal fun writeAndroidKmpSources(projectDir: Path) {
    writeAndroidLintConfig(projectDir)
    val androidMainDir = projectDir.resolve("src/androidMain/kotlin/compat")
    Files.createDirectories(androidMainDir)
    androidMainDir.resolve("AndroidSubject.kt").writeText(
        """
        package compat

        fun androidTargetName(value: String): String =
            value.trim().replaceFirstChar { it.uppercase() }
        """.trimIndent(),
    )
}

internal fun writeAndroidLibrarySources(projectDir: Path) {
    writeAndroidLintConfig(projectDir)
    val mainDir = projectDir.resolve("src/main/kotlin/compat")
    Files.createDirectories(mainDir)
    mainDir.resolve("AndroidSubject.kt").writeText(
        """
        package compat

        fun androidLibraryName(value: String): String =
            value.trim().replaceFirstChar { it.uppercase() }
        """.trimIndent(),
    )
}

internal fun writeAndroidLintConfig(projectDir: Path) {
    val configDir = projectDir.resolve("config")
    Files.createDirectories(configDir)
    configDir.resolve("lint.xml").writeText(
        """
        <lint>
            <issue id="AndroidGradlePluginVersion" severity="ignore" />
            <issue id="NewerVersionAvailable" severity="ignore" />
        </lint>
        """.trimIndent(),
    )
}
