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

    @TestFactory
    fun `generated KMP JVM-filtered consumers run required lifecycle tasks`(): Iterable<DynamicTest> =
        selectedRows(fixture = "kmp").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKmpConsumer(row)
            }
        }

    @TestFactory
    fun `generated KMP common-only consumers create no platform targets`(): Iterable<DynamicTest> =
        selectedRows(fixture = "kmp-common").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKmpCommonOnlyConsumer(row)
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
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks))
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runKmpConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-kmp-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerKmpBuildScript(row),
        )
        writeKmpSources(projectDir)
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=JVM")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runKmpCommonOnlyConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-kmp-common-only-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerKmpCommonOnlyBuildScript(row),
        )
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=COMMON")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runKotlinJvmMarkerConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve("${row.getValue("id")}-marker")
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-kotlin-jvm-marker-consumer"),
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
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks))
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun markerSettingsScript(rootProjectName: String): String {
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

            rootProject.name = "$rootProjectName"
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

    private fun markerKmpBuildScript(row: Map<String, String>): String =
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

    private fun markerKmpCommonOnlyBuildScript(row: Map<String, String>): String =
        """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}" apply false
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

    private fun writeKmpSources(projectDir: Path) {
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

    private fun sanitizedEnvironment(): Map<String, String> =
        System.getenv().filterKeys { it !in KMP_TARGET_ENV_KEYS }

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
        private val KMP_NO_TARGET_DIAGNOSTICS = listOf(
            "no applicable Kotlin targets found",
            "No Kotlin Targets Declared",
            "Unused Kotlin Source Sets",
        )
        private val FORBIDDEN_RUNTIME_LEAKS = listOf(
            "kotlin-compiler-embeddable",
            "detekt-core",
        )
        private val KMP_TARGET_ENV_KEYS = setOf(
            "KMP_TARGETS",
            "KMP_TARGETS_ALL",
        )
    }
}
