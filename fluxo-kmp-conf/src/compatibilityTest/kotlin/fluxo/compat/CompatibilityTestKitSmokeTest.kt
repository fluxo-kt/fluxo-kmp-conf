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

    @TestFactory
    fun `generated KMP consumers reject invalid target filters`(): Iterable<DynamicTest> =
        selectedRows(fixture = "kmp-invalid-target").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKmpInvalidTargetConsumer(row)
            }
        }

    @TestFactory
    fun `generated AGP 8 KMP consumers use legacy Android path`(): Iterable<DynamicTest> =
        (selectedRows(fixture = "android-kmp-agp8") +
            selectedRows(fixture = "android-kmp-agp8-exec")).map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAgp8KmpConsumer(row)
            }
        }

    @TestFactory
    fun `generated AGP 9 KMP consumers use KMP-aware Android path`(): Iterable<DynamicTest> =
        (selectedRows(fixture = "android-kmp-agp9") +
            selectedRows(fixture = "android-kmp-agp9-exec")).map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAgp9KmpConsumer(row)
            }
        }

    @TestFactory
    fun `generated AGP 9 KMP app consumers fail with migration guidance`(): Iterable<DynamicTest> =
        selectedRows(fixture = "android-kmp-agp9-app-unsupported").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAgp9KmpAppUnsupportedConsumer(row)
            }
        }

    @TestFactory
    fun `generated Android library consumers use legacy Android path`(): Iterable<DynamicTest> =
        (selectedRows(fixture = "android-lib-agp8") +
            selectedRows(fixture = "android-lib-agp8-exec") +
            selectedRows(fixture = "android-lib-agp9") +
            selectedRows(fixture = "android-lib-agp9-exec")).map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAndroidLibraryConsumer(row)
            }
        }

    @TestFactory
    fun `generated Compose Desktop consumers run required lifecycle tasks`(): Iterable<DynamicTest> =
        (selectedRows(fixture = "compose-desktop") +
            selectedRows(fixture = "compose-desktop-preapplied")).map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runComposeDesktopConsumer(row)
            }
        }

    @TestFactory
    fun `generated Compose KMP Android consumers run required lifecycle tasks`(): Iterable<DynamicTest> =
        (selectedRows(fixture = "compose-kmp-agp8") +
            selectedRows(fixture = "compose-kmp-agp9")).map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runComposeKmpAndroidConsumer(row)
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
        val pluginClasspath = pluginUnderTestClasspath()
        seedDependencyGuardBaseline(row, projectDir, gradleUserHome, pluginClasspath = pluginClasspath)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withPluginClasspath(pluginClasspath)
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks))
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
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
        seedDependencyGuardBaseline(row, projectDir, gradleUserHome, extraArguments = listOf("-PKMP_TARGETS=JVM"))

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
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
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
        seedDependencyGuardBaseline(row, projectDir, gradleUserHome, extraArguments = listOf("-PKMP_TARGETS=COMMON"))

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
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runKmpInvalidTargetConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-kmp-invalid-target-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerKmpBuildScript(row),
        )
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=TYPO")
            .forwardOutput()
            .buildAndFail()

        check("KMP_TARGETS property of 'TYPO' not recognized" in result.output) {
            result.output
        }
        check("Known options are:" in result.output) {
            result.output
        }
        check("ANDROID" in result.output && "IOS_SIMULATOR_ARM64" in result.output) {
            result.output
        }
        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    }

    private fun runAgp9KmpConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-agp9-kmp-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerAgp9KmpBuildScript(row),
        )
        if (row.isExecutionFixture()) {
            writeAndroidKmpSources(projectDir)
        }
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(DETEKT_CLASSIFICATION_NOISE), result.output)
        assertFalse(result.output.containsAny(ANDROID_LINT_VERSION_NOISE), result.output)
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
        check("Android namespace 'compat.agp9.kmp' (KMP+Android)" in result.output) {
            result.output
        }
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runAgp9KmpAppUnsupportedConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-agp9-kmp-app-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerAgp9KmpAppUnsupportedBuildScript(row),
        )
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID")
            .forwardOutput()
            .buildAndFail()

        check("AGP 9+ rejects `com.android.application`" in result.output) {
            result.output
        }
        check("there is no KMP-aware AGP application plugin" in result.output) {
            result.output
        }
        check("com.android.kotlin.multiplatform.library" in result.output) {
            result.output
        }
        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    }

    private fun runAgp8KmpConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-agp8-kmp-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerAgp8KmpBuildScript(row),
        )
        if (row.isExecutionFixture()) {
            writeAndroidKmpSources(projectDir)
        }
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(DETEKT_CLASSIFICATION_NOISE), result.output)
        assertFalse(result.output.containsAny(ANDROID_LINT_VERSION_NOISE), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runAndroidLibraryConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-android-library-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerAndroidLibraryBuildScript(row),
        )
        if (row.isExecutionFixture()) {
            writeAndroidLibrarySources(projectDir)
        }
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
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
        assertFalse(result.output.containsAny(DETEKT_CLASSIFICATION_NOISE), result.output)
        assertFalse(result.output.containsAny(ANDROID_LINT_VERSION_NOISE), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
    }

    private fun runComposeDesktopConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-compose-desktop-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerComposeDesktopBuildScript(row),
        )
        writeComposeDesktopSources(projectDir)
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')
        seedDependencyGuardBaseline(row, projectDir, gradleUserHome)

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
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
        assertNoForbiddenResolvedClasspathLeaks(projectDir)
    }

    private fun runComposeKmpAndroidConsumer(row: Map<String, String>) {
        val projectDir = tempDir.resolve(row.getValue("id"))
        Files.createDirectories(projectDir)
        projectDir.resolve("settings.gradle.kts").writeText(
            markerSettingsScript(rootProjectName = "compat-compose-kmp-android-consumer"),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            markerComposeKmpAndroidBuildScript(row),
        )
        projectDir.resolve("gradle.properties").writeText(
            "android.useAndroidX=true\n",
        )
        writeComposeKmpSources(projectDir)
        val gradleUserHome = tempDir.resolve("${row.getValue("id")}-gradle-user-home")
        Files.createDirectories(gradleUserHome)
        val requiredTasks = row.getValue("requiredTasks").split(' ')

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID,JVM")
            .forwardOutput()
            .build()

        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
        assertFalse(result.output.containsAny(DETEKT_CLASSIFICATION_NOISE), result.output)
        assertFalse(result.output.containsAny(ANDROID_LINT_VERSION_NOISE), result.output)
        assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
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
        seedDependencyGuardBaseline(row, projectDir, gradleUserHome)

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
        assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
        requiredTasks.forEach { result.assertTaskSuccess(":$it") }
        assertNoForbiddenResolvedClasspathLeaks(projectDir)
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

    private fun markerAgp8KmpBuildScript(row: Map<String, String>): String =
        """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}" apply false
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

    private fun markerAgp9KmpBuildScript(row: Map<String, String>): String =
        """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}" apply false
            id("com.android.kotlin.multiplatform.library") version "${row.getValue("agpVersion")}" apply false
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

    private fun markerAgp9KmpAppUnsupportedBuildScript(row: Map<String, String>): String =
        """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}" apply false
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

    private fun markerAndroidLibraryBuildScript(row: Map<String, String>): String {
        val isAgp8 = row.getValue("fixture").startsWith("android-lib-agp8")
        val kotlinAndroidPlugin = if (isAgp8) {
            "id(\"org.jetbrains.kotlin.android\") version \"${row.getValue("kgpVersion")}\""
        } else {
            "id(\"org.jetbrains.kotlin.android\") version \"${row.getValue("kgpVersion")}\" apply false"
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

    private fun markerComposeDesktopBuildScript(row: Map<String, String>): String {
        val composeCompilerPlugin = when (row.getValue("fixture")) {
            "compose-desktop-preapplied" ->
                """id("org.jetbrains.kotlin.plugin.compose") version "${row.getValue("kgpVersion")}""""
            else ->
                """id("org.jetbrains.kotlin.plugin.compose") version "${row.getValue("kgpVersion")}" apply false"""
        }
        return """
        import org.jetbrains.compose.desktop.application.dsl.TargetFormat

        plugins {
            id("org.jetbrains.kotlin.jvm") version "${row.getValue("kgpVersion")}"
            $composeCompilerPlugin
            id("org.jetbrains.compose") version "${row.getValue("composeVersion")}"
            id("${pluginId()}") version "${pluginVersion()}"
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
            return "org.jetbrains.compose.desktop:desktop-jvm-${'$'}platform-${'$'}arch:" +
                "${row.getValue("composeVersion")}"
        }

        fkcSetupKotlinApp {
            setupVerification = false
            enablePublication = false
            enableGradleDoctor = false
            setupCoroutines = false
        }

        dependencies {
            implementation(composeDesktopCurrentOs())
            implementation("org.jetbrains.compose.components:components-resources:${row.getValue("composeVersion")}")
            implementation("org.jetbrains.compose.ui:ui-tooling-preview:${row.getValue("composeVersion")}")
            testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:${row.getValue("kgpVersion")}")
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
    }

    private fun markerComposeKmpAndroidBuildScript(row: Map<String, String>): String {
        val isAgp9 = row.getValue("fixture") == "compose-kmp-agp9"
        val androidPlugin = when {
            isAgp9 -> "com.android.kotlin.multiplatform.library"
            else -> "com.android.library"
        }
        val shapeTask = when {
            isAgp9 -> "assertAgp9KmpComposeShape"
            else -> "assertAgp8KmpComposeShape"
        }
        val legacyAndroidTargetAssertion = when {
            isAgp9 -> """
                val androidTarget = androidTargets.single()
                    as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
                check(androidTarget.namespace == "compat.compose.agp9.kmp")
                check(androidTarget.compileSdk == 35)
                check(androidTarget.minSdk == 24)
            """.trimIndent()
            else -> """
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
        return """
        plugins {
            id("org.jetbrains.kotlin.multiplatform") version "${row.getValue("kgpVersion")}" apply false
            id("org.jetbrains.kotlin.plugin.compose") version "${row.getValue("kgpVersion")}" apply false
            id("org.jetbrains.compose") version "${row.getValue("composeVersion")}"
            id("$androidPlugin") version "${row.getValue("agpVersion")}" apply false
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
                androidNamespace = "compat.compose.${if (isAgp9) "agp9" else "agp8"}.kmp"
                androidCompileSdk = 35
                androidMinSdk = 24
            },
            kmp = { allDefaultTargets() },
        )

        plugins.withId("org.jetbrains.kotlin.multiplatform") {
            dependencies.add(
                "commonMainImplementation",
                "org.jetbrains.compose.runtime:runtime:${row.getValue("composeVersion")}",
            )
        }

        tasks.register("$shapeTask") {
            doLast {
                check(plugins.hasPlugin("org.jetbrains.compose"))
                check(plugins.hasPlugin("org.jetbrains.kotlin.plugin.compose"))
                check(plugins.hasPlugin("$androidPlugin"))
                check(plugins.hasPlugin("org.jetbrains.kotlin.multiplatform"))
                check(plugins.hasPlugin("com.android.library") == ${!isAgp9})
                check(plugins.hasPlugin("com.android.kotlin.multiplatform.library") == $isAgp9)

                val kotlin = project.extensions.getByType(
                    org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension::class.java,
                )
                val androidTargets = kotlin.targets.matching { it.name == "android" }
                check(androidTargets.size == 1) {
                    "Expected one Android KMP target, got ${'$'}androidTargets"
                }
                $legacyAndroidTargetAssertion
                check(kotlin.targets.names.contains("jvm"))
            }
        }
        """.trimIndent()
    }

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

    private fun assertNoForbiddenResolvedClasspathLeaks(projectDir: Path) {
        val classpath = projectDir.resolve("dependencies/classpath.txt")
        check(Files.isRegularFile(classpath)) {
            "Resolved classpath dependencyGuard output is missing: $classpath"
        }
        val dependencies = Files.readString(classpath)
        FORBIDDEN_RUNTIME_LEAKS.forEach { forbidden ->
            check(forbidden !in dependencies) {
                "Resolved marker consumer classpath leaks forbidden dependency '$forbidden': " +
                    classpath
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

    private fun writeAndroidKmpSources(projectDir: Path) {
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

    private fun writeAndroidLibrarySources(projectDir: Path) {
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

    private fun writeComposeKmpSources(projectDir: Path) {
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

    private fun writeAndroidLintConfig(projectDir: Path) {
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

    private fun writeComposeDesktopSources(projectDir: Path) {
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

    private fun seedDependencyGuardBaseline(
        row: Map<String, String>,
        projectDir: Path,
        gradleUserHome: Path,
        extraArguments: List<String> = emptyList(),
        pluginClasspath: List<File> = emptyList(),
    ) {
        if (CHECK_TASK !in row.getValue("requiredTasks").split(' ')) {
            return
        }

        val runner = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withTestKitDir(gradleUserHome.toFile())
            .withGradleVersion(row.getValue("gradleVersion"))
            .withEnvironment(sanitizedEnvironment())
            .withArguments(gradleArguments(listOf(DEPENDENCY_GUARD_BASELINE_TASK)) + extraArguments)
        if (pluginClasspath.isNotEmpty()) {
            runner.withPluginClasspath(pluginClasspath)
        }

        val result = runner.build()
        assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
        result.assertTaskSuccess(":$DEPENDENCY_GUARD_BASELINE_TASK")
    }

    private fun sanitizedEnvironment(): Map<String, String> =
        System.getenv().filterKeys { it !in KMP_TARGET_ENV_KEYS }

    private fun Map<String, String>.isExecutionFixture(): Boolean =
        getValue("fixture").endsWith("-exec")

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
        private val DEPENDENCY_GUARD_BASELINE_NOISE = listOf(
            "Dependency Guard baseline created",
        )
        private const val CHECK_TASK = "check"
        private const val DEPENDENCY_GUARD_BASELINE_TASK = "dependencyGuardBaseline"
        private val KMP_NO_TARGET_DIAGNOSTICS = listOf(
            "no applicable Kotlin targets found",
            "No Kotlin Targets Declared",
            "Unused Kotlin Source Sets",
        )
        private val ANDROID_LINT_VERSION_NOISE = listOf(
            "A newer version of com.android.library",
            "A newer version of org.jetbrains.kotlin",
            "AndroidGradlePluginVersion",
            "NewerVersionAvailable",
        )
        private val DETEKT_CLASSIFICATION_NOISE = listOf(
            "Unexpected Detekt task",
            "platform UNKNOWN is disabled",
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
