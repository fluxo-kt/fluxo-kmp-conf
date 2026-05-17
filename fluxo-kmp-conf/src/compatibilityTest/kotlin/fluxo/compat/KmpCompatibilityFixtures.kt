package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

internal fun markerKmpBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}"
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
        },
        kmp = { allDefaultTargets() },
    )

    kotlin {
        sourceSets.commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    tasks.register("assertKmpShape") {
        doLast {
            val taskNames = tasks.names
            val expected = setOf("compileKotlinMetadata", "compileKotlinJvm", "jvmTest")
            check(taskNames.containsAll(expected)) {
                "Missing expected JVM-filtered KMP tasks: ${'$'}{expected - taskNames}"
            }
            val allowedCompileTasks = setOf("compileKotlinMetadata", "compileKotlinJvm")
            val forbidden = taskNames.filter { taskName ->
                taskName.startsWith("compileKotlin") && taskName !in allowedCompileTasks
            }
            check(forbidden.isEmpty()) {
                "KMP_TARGETS=JVM created disabled target tasks: ${'$'}forbidden"
            }
        }
    }
    """.trimIndent()
internal fun markerKmpCommonOnlyBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.multiplatform") version "${row.getValue(
        "kgpVersion"
    )}" apply false
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
        },
        kmp = { allDefaultTargets() },
    )

    tasks.register("assertNoPlatformTargets") {
        doLast {
            val taskNames = tasks.names
            val forbidden = taskNames.filter { taskName ->
                (taskName.startsWith("compileKotlin") && taskName != "compileKotlinMetadata") ||
                    taskName.startsWith("compileTestKotlin") ||
                    taskName == "jvmTest"
            }
            check(forbidden.isEmpty()) {
                "KMP_TARGETS=COMMON created platform target tasks: ${'$'}forbidden"
            }
        }
    }
    """.trimIndent()
internal fun writeKmpSources(projectDir: Path) {
    val commonMainDir = projectDir.resolve("src/commonMain/kotlin/compat")
    val commonTestDir = projectDir.resolve("src/commonTest/kotlin/compat")
    val jvmTestDir = projectDir.resolve("src/jvmTest/kotlin/compat")
    Files.createDirectories(commonMainDir)
    Files.createDirectories(commonTestDir)
    Files.createDirectories(jvmTestDir)
    commonMainDir.resolve("CompatSubject.kt").writeText(
        """
        package compat

        fun platformNeutralName(value: String): String =
            value.trim().replaceFirstChar { it.uppercase() }
        """.trimIndent(),
    )
    commonTestDir.resolve("CompatAssertions.kt").writeText(
        """
        package compat

        import kotlin.test.assertEquals

        fun assertPlatformNeutralName(raw: String, expected: String) {
            assertEquals(expected, platformNeutralName(raw))
        }
        """.trimIndent(),
    )
    jvmTestDir.resolve("CompatSubjectTest.kt").writeText(
        """
        package compat

        import kotlin.test.Test

        class CompatSubjectTest {
            @Test
            fun normalizesCommonCodeOnJvm() {
                assertPlatformNeutralName(" fluxo", "Fluxo")
            }
        }
        """.trimIndent(),
    )
}
