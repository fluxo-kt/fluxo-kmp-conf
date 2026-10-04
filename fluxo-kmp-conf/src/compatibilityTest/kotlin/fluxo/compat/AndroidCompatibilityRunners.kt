package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

private val ANDROID_NOISE = DETEKT_CLASSIFICATION_NOISE + ANDROID_LINT_VERSION_NOISE

internal fun runAgp9KmpConsumer(row: Map<String, String>, tempDir: Path) {
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-agp9-kmp-consumer",
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerAgp9KmpBuildScript(row))
        if (row.isExecutionFixture()) {
            writeAndroidKmpSources(projectDir)
        }
    }
    check("Android namespace 'compat.agp9.kmp' (KMP+Android)" in result.output) {
        result.output
    }
}

internal fun runAgp9KmpAppUnsupportedConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-agp9-kmp-app-consumer",
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        expectFailure = listOf(
            "AGP 9+ rejects `com.android.application`",
            "there is no KMP-aware AGP application plugin",
            "com.android.kotlin.multiplatform.library",
        ),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts")
            .writeText(markerAgp9KmpAppUnsupportedBuildScript(row))
    }
}

internal fun runAgp8KmpConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-agp8-kmp-consumer",
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerAgp8KmpBuildScript(row))
        if (row.isExecutionFixture()) {
            writeAndroidKmpSources(projectDir)
        }
    }
}

internal fun runAndroidLibraryConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-android-library-consumer",
        forbiddenOutput = ANDROID_NOISE,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerAndroidLibraryBuildScript(row))
        if (row.isExecutionFixture()) {
            writeAndroidLibrarySources(projectDir)
        }
    }
}
