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
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

internal class CompatibilityTestKitSmokeTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    @TestFactory
    fun `generated Kotlin JVM consumers run required lifecycle tasks`(): Iterable<DynamicTest> =
        selectedRows(fixture = "kotlin-jvm").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKotlinJvmConsumer(row)
            }
        }

    @TestFactory
    fun `generated Kotlin JVM marker consumers run required lifecycle tasks`(): Iterable<DynamicTest> =
        selectedRows(fixture = "kotlin-jvm").map { row ->
            DynamicTest.dynamicTest("${row.getValue("id")}-marker") {
                runKotlinJvmMarkerConsumer(row)
            }
        }

    private fun runKotlinJvmConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
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
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withPluginClasspath(pluginUnderTestClasspath())
            .withArguments(gradleArguments(requiredTasks))
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runKotlinJvmMarkerConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve("${row.getValue("id")}-marker")
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerKotlinJvmBuildScript(row),
        )
        writeKotlinJvmSources(projectDir)
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-marker-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withArguments(gradleArguments(requiredTasks))
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun markerSettingsScript(): String {
        val localMavenRepo = localMavenRepoPath()
        return """
            pluginManagement {
                repositories {
                    maven("$localMavenRepo")
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

            rootProject.name = "compat-kotlin-jvm-marker-consumer"
            """.trimIndent()
    }

    private fun markerKotlinJvmBuildScript(row: Map<String, String>): String =
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
            add("testImplementation", "org.jetbrains.kotlin:kotlin-test-junit5:${row.getValue("kgpVersion")}")
        }

        tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
            useJUnitPlatform()
        }
        """.trimIndent()

    private fun localMavenRepoPath(): String {
        val path = checkNotNull(System.getProperty("fluxo.local.maven.repo")) {
            "fluxo.local.maven.repo system property is missing"
        }
        val repo = File(path)
        check(repo.isDirectory) {
            "Local Maven repository is missing: ${repo.absolutePath}"
        }
        assertPublishedArtifacts(repo.toPath())
        return repo.invariantSeparatorsPath
    }

    private fun assertPublishedArtifacts(repo: Path) {
        val pluginId = pluginId()
        val version = pluginVersion()
        val markerPom = repo
            .resolve(pluginId.replace('.', File.separatorChar))
            .resolve("$pluginId.gradle.plugin")
            .resolve(version)
            .resolve("$pluginId.gradle.plugin-$version.pom")
        val runtimeJar = repo
            .resolve("io/github/fluxo-kt/fluxo-kmp-conf")
            .resolve(version)
            .resolve("fluxo-kmp-conf-$version.jar")
        val runtimePom = runtimeJar.resolveSibling("fluxo-kmp-conf-$version.pom")
        val runtimeModule = runtimeJar.resolveSibling("fluxo-kmp-conf-$version.module")
        check(Files.isRegularFile(markerPom)) {
            "Published plugin marker POM is missing: $markerPom"
        }
        check(Files.isRegularFile(runtimeJar)) {
            "Published plugin runtime jar is missing: $runtimeJar"
        }
        check(Files.isRegularFile(runtimePom)) {
            "Published plugin runtime POM is missing: $runtimePom"
        }
        check(Files.isRegularFile(runtimeModule)) {
            "Published plugin Gradle module metadata is missing: $runtimeModule"
        }
        assertNoForbiddenRuntimeLeaks(markerPom, runtimePom, runtimeModule)
    }

    private fun assertNoForbiddenRuntimeLeaks(vararg metadataFiles: Path) {
        metadataFiles.forEach { file ->
            val metadata = Files.readString(file)
            FORBIDDEN_RUNTIME_LEAKS.forEach { forbidden ->
                check(forbidden !in metadata) {
                    "Published metadata leaks forbidden runtime dependency '$forbidden': $file"
                }
            }
        }
    }

    private fun pluginId(): String =
        checkNotNull(System.getProperty("fluxo.plugin.id")) {
            "fluxo.plugin.id system property is missing"
        }

    private fun pluginVersion(): String =
        checkNotNull(System.getProperty("fluxo.plugin.version")) {
            "fluxo.plugin.version system property is missing"
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

    private fun selectedRows(fixture: String): List<Map<String, String>> {
        val profile = System.getProperty("compat.profile", "pr")
        val profiles = when (profile) {
            "release" -> setOf("pr", "release")
            "full" -> setOf("pr", "full")
            else -> setOf(profile)
        }
        val rows = matrixRows()
            .filter { it["fixture"] == fixture && it["profile"] in profiles }
        check(rows.isNotEmpty()) {
            "No $fixture compatibility rows selected for compat.profile=$profile"
        }
        return rows
    }

    private fun matrixRows(): List<Map<String, String>> {
        val root = Path.of(System.getProperty("fluxo.repo.root"))
        val lines = Files.readAllLines(root.resolve("compat/matrix.tsv"))
            .filter { it.isNotBlank() && !it.startsWith("#") }
        val header = lines.first().split('\t')
        return lines.drop(1)
            .map { header.zip(it.split('\t')).toMap() }
    }

    private fun gradleArguments(requiredTasks: List<String>): List<String> =
        requiredTasks + "--stacktrace"

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
        private val FORBIDDEN_RUNTIME_LEAKS = listOf(
            "kotlin-compiler-embeddable",
            "detekt-core",
        )
    }
}
