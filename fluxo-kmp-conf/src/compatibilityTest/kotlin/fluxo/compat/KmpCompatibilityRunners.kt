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
    // Kotlin keeps deprecating native targets and then deletes them (watchosArm32 has no DSL
    // method and no KonanTarget left in Kotlin 2.5), so target groups follow the consumer's
    // Kotlin, never a list in the plugin. The newest row also runs on the next Kotlin.
    if (row.getValue("kotlinLangVersion") == "-") {
        runKmpAllTargetsCase(row, tempDir)
        runKmpAllTargetsCase(row + ("kgpVersion" to NEXT_KOTLIN), tempDir)
    }
}

/**
 * Groups like `allDefaultTargets()` must create only targets the consumer's Kotlin fully
 * supports: a deprecated target warns (and becomes an error once Kotlin stops tolerating it),
 * and a removed one has no DSL method left, so calling it fails the build with
 * NoSuchMethodError; the next-Kotlin run covers that. iosX64 is left out too (ruled
 * 2026-10-04: Compose Multiplatform dropped it). The arm64 targets are the control: without
 * them the case would also pass with no native targets at all.
 */
private fun runKmpAllTargetsCase(row: Map<String, String>, tempDir: Path) {
    val kgp = row.getValue("kgpVersion")
    val output = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-all-targets-consumer",
        projectDir = tempDir.resolve("${row.getValue("id")}-all-targets-$kgp"),
        tasks = listOf("help"),
        arguments = listOf("-PKMP_TARGETS_ALL=true"),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerKmpAllTargetsBuildScript(row))
    }.output
    fun printed(marker: String) = output.substringAfter(marker, "").substringBefore('\n')
    val targets = printed(KMP_TARGETS_MARKER).split(',')
    check(targets.containsAll(listOf("iosArm64", "watchosArm64"))) { "No native targets:\n$output" }
    val deprecated = printed(KMP_DEPRECATED_TARGETS_MARKER)
    check(deprecated.isEmpty()) { "Kotlin $kgp: groups created deprecated $deprecated:\n$output" }
    check("iosX64" !in targets) { "Kotlin $kgp: groups created iosX64:\n$output" }
    // The fixture has no Android Gradle Plugin: the Android target is dropped with guidance
    // instead of failing configuration.
    check(AGP_MISSING in output) { "No AGP guidance:\n$output" }
}

private const val AGP_MISSING = "Android Gradle Plugin (AGP) is not found in the classpath"

/** The next Kotlin release line, Beta included: removals land in its first Beta. */
private const val NEXT_KOTLIN = "2.5.0-Beta1"

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
