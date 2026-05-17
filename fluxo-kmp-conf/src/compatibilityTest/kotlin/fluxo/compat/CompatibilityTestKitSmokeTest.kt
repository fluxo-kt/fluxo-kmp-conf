package fluxo.compat

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

internal class CompatibilityTestKitSmokeTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `generated Kotlin JVM consumer with buildscript Kotlin classpath runs help without linkage crashes`() {
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
            }
            """.trimIndent(),
        )
        val gradleUserHome = tempDir.resolve("gradle-user-home")
        Files.createDirectories(gradleUserHome)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withPluginClasspath(pluginUnderTestClasspath())
            .withArguments("help", "--stacktrace")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        require(result.task(":help")?.outcome == TaskOutcome.SUCCESS) {
            result.output
        }
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

    private companion object {
        private val KNOWN_CRASH_SIGNATURES = listOf(
            "NoSuchMethodError",
            "ClassCastException",
            "NoClassDefFoundError",
            "Could not initialize class",
        )
    }
}
