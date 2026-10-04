package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

internal fun runKmpConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-consumer",
        arguments = listOf("-PKMP_TARGETS=JVM"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpBuildScript(row))
        writeKmpSources(projectDir)
    }
}

internal fun runKmpCommonOnlyConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-common-only-consumer",
        arguments = listOf("-PKMP_TARGETS=COMMON"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpCommonOnlyBuildScript(row))
    }
}

internal fun runKmpInvalidTargetConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-invalid-target-consumer",
        arguments = listOf("-PKMP_TARGETS=TYPO"),
        expectFailure = listOf(
            "KMP_TARGETS property of 'TYPO' not recognized",
            "Known options are:",
            "ANDROID",
            "IOS_SIMULATOR_ARM64",
        ),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpBuildScript(row))
    }
}
