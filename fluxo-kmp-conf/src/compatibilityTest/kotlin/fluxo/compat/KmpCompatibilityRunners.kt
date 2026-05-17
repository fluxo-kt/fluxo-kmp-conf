package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse

internal fun runKmpConsumer(row: Map<String, String>, tempDir: Path) {
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
    seedDependencyGuardBaseline(
        row,
        projectDir,
        gradleUserHome,
        extraArguments = listOf("-PKMP_TARGETS=JVM")
    )

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

internal fun runKmpCommonOnlyConsumer(row: Map<String, String>, tempDir: Path) {
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
    seedDependencyGuardBaseline(
        row,
        projectDir,
        gradleUserHome,
        extraArguments = listOf("-PKMP_TARGETS=COMMON")
    )

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

internal fun runKmpInvalidTargetConsumer(row: Map<String, String>, tempDir: Path) {
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
