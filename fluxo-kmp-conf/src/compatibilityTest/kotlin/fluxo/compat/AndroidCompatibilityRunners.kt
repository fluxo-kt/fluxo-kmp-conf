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

/**
 * A call above minSdk (Lint `NewApi`) must fail `check`, once, at the end of the build, listing
 * every module's finding. Two modules, because the point is that Lint still runs in the second
 * module after the first one found something.
 */
internal fun runAndroidLibraryNewApiFailsCheck(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-android-library-newapi",
        projectDir = tempDir.resolve(row.getValue("id") + "-newapi"),
        tasks = listOf(CHECK_TASK),
        forbiddenOutput = ANDROID_NOISE,
        expectFailure = listOf(
            "Android Lint found 2 calls above minSdk (NewApi)",
            "src/main/kotlin/compat/RootNewApi.kt",
            "second/src/main/kotlin/compat/SecondNewApi.kt",
        ),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerAndroidLibraryBuildScript(row))
        writeAndroidLintConfig(projectDir)
        writeNewApiCall(projectDir.resolve("src/main/kotlin/compat"), "RootNewApi")

        projectDir.resolve("settings.gradle.kts").toFile().appendText("\ninclude(\":second\")\n")
        val second = projectDir.resolve("second")
        writeNewApiCall(second.resolve("src/main/kotlin/compat"), "SecondNewApi")
        val kotlinAndroid = if (row.getValue("fixture").startsWith("android-lib-agp8")) {
            "plugins { id(\"org.jetbrains.kotlin.android\") }\n"
        } else {
            ""
        }
        second.resolve("build.gradle.kts").writeText(
            kotlinAndroid + "fkcSetupAndroidLibrary(namespace = \"compat.second\")\n",
        )
    }
}

/** The KMP Android paths name their Lint tasks differently; their reports must reach the check. */
internal fun runKmpNewApiFailsCheck(row: Map<String, String>, tempDir: Path, buildScript: String) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-newapi",
        projectDir = tempDir.resolve(row.getValue("id") + "-newapi"),
        tasks = listOf(CHECK_TASK),
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
        expectFailure = listOf(
            "Android Lint found 1 call above minSdk (NewApi)",
            "src/androidMain/kotlin/compat/KmpNewApi.kt",
        ),
    ) { projectDir ->
        // ABI validation would fail `check` first on the missing API dump; it is not the subject.
        projectDir.resolve("build.gradle.kts").writeText(
            buildScript.replace("enableApiValidation = true", "enableApiValidation = false"),
        )
        writeAndroidLintConfig(projectDir)
        writeNewApiCall(projectDir.resolve("src/androidMain/kotlin/compat"), "KmpNewApi")
    }
}
