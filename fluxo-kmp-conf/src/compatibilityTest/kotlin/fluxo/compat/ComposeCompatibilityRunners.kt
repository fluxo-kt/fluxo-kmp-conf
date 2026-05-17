package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse

internal fun runComposeDesktopConsumer(row: Map<String, String>, tempDir: Path) {
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

internal fun runComposeKmpAndroidConsumer(row: Map<String, String>, tempDir: Path) {
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
