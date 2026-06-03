package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertFalse

internal fun runAgp9KmpConsumer(row: Map<String, String>, tempDir: Path) {
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

    val args = gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID"
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()

    result.assertInnerJdk(row)
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

internal fun runAgp9KmpAppUnsupportedConsumer(row: Map<String, String>, tempDir: Path) {
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

    val args = gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID"
    val result = compatRunner(row, projectDir, gradleUserHome, args).buildAndFail()

    result.assertInnerJdk(row)
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

internal fun runAgp8KmpConsumer(row: Map<String, String>, tempDir: Path) {
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

    val args = gradleArguments(requiredTasks) + "-PKMP_TARGETS=ANDROID"
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()

    result.assertInnerJdk(row)
    assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(KMP_NO_TARGET_DIAGNOSTICS), result.output)
    assertFalse(result.output.containsAny(DETEKT_CLASSIFICATION_NOISE), result.output)
    assertFalse(result.output.containsAny(ANDROID_LINT_VERSION_NOISE), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
    requiredTasks.forEach { result.assertTaskSuccess(":$it") }
}

internal fun runAndroidLibraryConsumer(row: Map<String, String>, tempDir: Path) {
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

    val args = gradleArguments(requiredTasks)
    val result = compatRunner(row, projectDir, gradleUserHome, args).build()

    result.assertInnerJdk(row)
    assertFalse(result.output.containsAny(KNOWN_CRASH_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DETEKT_CLASSIFICATION_NOISE), result.output)
    assertFalse(result.output.containsAny(ANDROID_LINT_VERSION_NOISE), result.output)
    assertFalse(result.output.containsAny(PUBLICATION_NOISE_SIGNATURES), result.output)
    assertFalse(result.output.containsAny(DEPENDENCY_GUARD_BASELINE_NOISE), result.output)
    requiredTasks.forEach { result.assertTaskSuccess(":$it") }
}
