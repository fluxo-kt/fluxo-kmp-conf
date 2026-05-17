package fluxo.compat

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.writeText
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class CompatibilityTestKitSmokeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `generated Kotlin JVM consumer runs required lifecycle tasks without linkage crashes`() {
        val row = currentBuildRow()
        val projectDir = tempDir.resolve("kotlin-jvm-consumer")
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    gradlePluginPortal()
                    mavenCentral()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            rootProject.name = "compat-kotlin-jvm-consumer"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            buildscript {
                repositories {
                    google()
                    gradlePluginPortal()
                    mavenCentral()
                }
                dependencies {
                    classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${row.getValue("kgpVersion")}")
                }
            }

            plugins {
                id("${System.getProperty("fluxo.plugin.id")}")
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
                add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:${row.getValue("kgpVersion")}")
            }

            tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
                useJUnitPlatform()
            }
            """.trimIndent(),
        )
        writeKotlinJvmSources(projectDir)
        val gradleUserHome = tempDir.resolve("gradle-user-home")
        Files.createDirectories(gradleUserHome)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withPluginClasspath(pluginUnderTestClasspath())
            .withArguments("help", "compileKotlin", "test", "check", "--stacktrace")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        result.assertTaskSuccess(":help")
        result.assertTaskSuccess(":compileKotlin")
        result.assertTaskSuccess(":test")
        result.assertTaskSuccess(":check")
    }

    private fun writeKotlinJvmSources(projectDir: Path) {
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

    private fun currentBuildRow(): Map<String, String> {
        val root = Path.of(System.getProperty("fluxo.repo.root"))
        val lines = Files.readAllLines(root.resolve("compat/matrix.tsv"))
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.first().split('\t')
        return lines.drop(1)
            .map { header.zip(it.split('\t')).toMap() }
            .single { it["id"] == "current-build" }
    }

    private fun pluginUnderTestClasspath(): List<File> {
        val metadata = Properties()
        javaClass.classLoader
            .getResourceAsStream("plugin-under-test-metadata.properties")
            .use { stream ->
                checkNotNull(stream) { "plugin-under-test-metadata.properties not found" }
                metadata.load(stream)
            }
        val implementationClasspath = metadata.getProperty("implementation-classpath")
            .split(File.pathSeparator)
            .map(::File)
        val kotlinPluginClasspath = System
            .getProperty("fluxo.compat.kotlinPluginClasspath")
            .split(File.pathSeparator)
            .filter(String::isNotBlank)
            .map(::File)
        return implementationClasspath + kotlinPluginClasspath
    }

    private fun String.containsAny(needles: Iterable<String>): Boolean =
        needles.any { it in this }

    private fun BuildResult.assertTaskSuccess(path: String) {
        require(task(path)?.outcome == TaskOutcome.SUCCESS) {
            output
        }
    }

    private companion object {
        private val KNOWN_CRASH_SIGNATURES = listOf(
            "NoSuchMethodError",
            "ClassCastException",
            "NoClassDefFoundError",
            "Could not initialize class",
        )
        private val PUBLICATION_NOISE_SIGNATURES = listOf(
            "SIGNING_KEY",
            "Publications are unsigned",
            "setup maven POM",
            "maven publication",
        )
    }
}
