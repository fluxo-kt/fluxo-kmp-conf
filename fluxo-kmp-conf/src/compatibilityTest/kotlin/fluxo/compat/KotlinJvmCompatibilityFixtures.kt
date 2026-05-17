package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

internal fun markerKotlinJvmBuildScript(row: Map<String, String>): String =
    """
    plugins {
        id("org.jetbrains.kotlin.jvm") version "${row.getValue("kgpVersion")}"
        id("${pluginId()}") version "${pluginVersion()}"
    }

    group = "compat"
    version = "1.0.0"

    fkcSetupKotlin {
        setupVerification = false
        enablePublication = false
        enableGradleDoctor = false
        setupCoroutines = false
    }

    dependencies {
        add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:${row.getValue(
        "kgpVersion"
    )}")
    }

    tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
        useJUnitPlatform()
    }
    """.trimIndent()
internal fun writeKotlinJvmSources(projectDir: Path) {
    val mainDir = projectDir.resolve("src/main/kotlin/compat")
    val testDir = projectDir.resolve("src/test/kotlin/compat")
    Files.createDirectories(mainDir)
    Files.createDirectories(testDir)
    mainDir.resolve("CompatSubject.kt").writeText(
        """
        package compat

        fun normalizeName(value: String): String =
            value.trim().replaceFirstChar { it.uppercase() }
        """.trimIndent(),
    )
    testDir.resolve("CompatSubjectTest.kt").writeText(
        """
        package compat

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class CompatSubjectTest {
            @Test
            fun normalizesInput() {
                assertEquals("Fluxo", normalizeName(" fluxo"))
            }
        }
        """.trimIndent(),
    )
}
