package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import org.gradle.testkit.runner.TaskOutcome

internal val ANDROID_NOISE = ANDROID_LINT_VERSION_NOISE

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
    if (row.isExecutionFixture()) {
        runAgp9KmpAndroidFilteredOutCase(row, tempDir)
        runAgp9KmpSiblingDetektCase(row, tempDir, detekt1 = true)
        runAgp9KmpSiblingDetektCase(row, tempDir, detekt1 = false)
        runKmpPlainDetektCase(row, tempDir)
        runAgp9KmpResourcesAndHostTestsCase(row, tempDir)
    }
}

/**
 * Plain `detekt` is what people type. In a KMP module Detekt's own `detekt` task has no sources
 * (KMP code lives in per-target source sets), so it passed on zero files while `check` failed on
 * the same code. It must give `check`'s verdict.
 */
private fun runKmpPlainDetektCase(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-plain-detekt",
        projectDir = tempDir.resolve(row.getValue("id") + "-plain-detekt"),
        tasks = listOf("detekt"),
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
        expectFailure = listOf("Planted.kt", "[NewLineAtEndOfFile]"),
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerAgp9KmpBuildScript(row))
        writeAndroidKmpSources(projectDir)
        val common = projectDir.resolve("src/commonMain/kotlin/compat")
        Files.createDirectories(common)
        common.resolve("Planted.kt").writeText("package compat\n\nfun planted(): Int = 1")
    }
}

/**
 * Detekt's Android type-resolution task in a KMP+Android module that depends on another
 * KMP+Android module. Detekt 1.x resolves the raw compile classpath configuration, where AGP 9
 * offers the sibling as several variants ("cannot choose between … android-classes-jar /
 * android-lint / android-lint-local-aar"), so its classpath must be the one the compiler used.
 * Run on both Detekt lines: fluxo derives Detekt 2 here (stdlib 2.2+), whose task takes the
 * compiler's classpath by convention; Detekt 1 stays reachable by applying it, and its task names
 * put the target first.
 */
private fun runAgp9KmpSiblingDetektCase(
    row: Map<String, String>,
    tempDir: Path,
    detekt1: Boolean,
) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-agp9-kmp-siblings",
        projectDir = tempDir.resolve(row.getValue("id") + "-siblings" + if (detekt1) 1 else 2),
        tasks = listOf(if (detekt1) "detektAndroidMain" else "detektMainAndroid"),
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
    ) { projectDir ->
        // Applied before fkcSetup*, which keeps the line a module already applied. A missed
        // replace leaves the module on Detekt 2, where `detektAndroidMain` doesn't exist.
        val group = "group = \"compat\""
        val script = markerAgp9KmpBuildScript(row).let {
            if (!detekt1) return@let it
            it.replace(group, "apply(plugin = \"io.gitlab.arturbosch.detekt\"); $group")
        }
        projectDir.resolve("build.gradle.kts").writeText(
            script + "\n\n" +
                "project.extensions.configure<" +
                "org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>(\"kotlin\") {\n" +
                "    sourceSets.named(\"commonMain\") {\n" +
                "        dependencies { implementation(project(\":lib\")) }\n" +
                "    }\n" +
                "}\n",
        )
        writeAndroidKmpSources(projectDir)
        projectDir.resolve("settings.gradle.kts").toFile().appendText("\ninclude(\":lib\")\n")
        val lib = projectDir.resolve("lib")
        Files.createDirectories(lib.resolve("src/commonMain/kotlin/compat"))
        lib.resolve("src/commonMain/kotlin/compat/LibSubject.kt")
            .writeText("package compat\n\nfun libName(prefix: String): String = prefix + \"lib\"\n")
        lib.resolve("build.gradle.kts").writeText(
            """
            fkcSetupMultiplatform(
                config = {
                    enableApiValidation = false
                    enablePublication = false
                    enableGradleDoctor = false
                    setupCoroutines = false
                    androidNamespace = "compat.lib"
                    androidCompileSdk = 35
                    androidMinSdk = 24
                },
                kmp = { allDefaultTargets() },
            )

            // Like the root module: without a host-test compilation `commonTest` is unused and
            // KGP warns about it.
            project.extensions.configure<
                org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension,
            >("kotlin") {
                targets.named("android") {
                    (this as com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget)
                        .withHostTest {}
                }
            }
            """.trimIndent(),
        )
    }
}

/**
 * A per-platform CI job (`KMP_TARGETS=JVM`) on a consumer that applies AGP 9's KMP plugin
 * itself, so the `android` target exists but is filtered out. The root report merges must still
 * build their task graph: Detekt's failed on the disabled Android task's empty report, and Lint
 * is off here. The target, created outside fluxo's containers, must be disabled in every build
 * (it once was only in verbose ones). Real sources make an enabled compile report SUCCESS.
 */
private fun runAgp9KmpAndroidFilteredOutCase(row: Map<String, String>, tempDir: Path) {
    val compile = ":compileAndroidMain"
    val merges = listOf(":mergeDetektSarif", ":mergeLintSarif")
    val result = runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-agp9-kmp-consumer",
        tasks = (merges + compile).map { it.removePrefix(":") },
        arguments = listOf("-PKMP_TARGETS=JVM"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
        assertTasksSucceed = false,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts")
            .writeText(markerAgp9KmpBuildScript(row, consumerAppliesAgp = true))
        writeAndroidKmpSources(projectDir)
    }
    check(result.task(compile)?.outcome == TaskOutcome.SKIPPED) {
        "KMP_TARGETS=JVM left $compile enabled: ${result.task(compile)?.outcome}\n${result.output}"
    }
    merges.forEach { result.assertTaskSuccess(it) }
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

    // AGP 9's built-in Kotlin fails configuration on `org.jetbrains.kotlin.kapt`; its kapt is
    // `com.android.legacy-kapt`, from an artifact AGP doesn't depend on. `setupKapt` must apply
    // it when the consumer declared it, and otherwise name the line to add.
    if (row.getValue("fixture") != "android-lib-agp9-exec") return
    for (declared in listOf(false, true)) {
        val output = runConsumerCase(
            row,
            tempDir,
            rootProjectName = "compat-android-kapt",
            projectDir = tempDir.resolve(row.getValue("id") + "-kapt"),
            tasks = listOf("help"),
            forbiddenOutput = ANDROID_NOISE,
            expectFailure = if (declared) emptyList() else listOf(LEGACY_KAPT_LINE),
        ) { projectDir ->
            val plugin = "$LEGACY_KAPT_LINE \"${row.getValue("agpVersion")}\" apply false"
            projectDir.resolve("build.gradle.kts").writeText(
                markerAndroidLibraryBuildScript(row)
                    .replace("plugins {", if (declared) "plugins {\n    $plugin" else "plugins {")
                    .replace(NO_COROUTINES, "$NO_COROUTINES\nsetupKapt = true") +
                    "\nprintln(\"legacy-kapt applied: \" + " +
                    "pluginManager.hasPlugin(\"com.android.legacy-kapt\"))\n",
            )
        }.output
        check(!declared || "legacy-kapt applied: true" in output) { output }
    }
}

private const val LEGACY_KAPT_LINE = "id(\"com.android.legacy-kapt\") version"

private const val NO_COROUTINES = "setupCoroutines = false"

/**
 * A call above minSdk (Lint `NewApi`) must fail `check`, once, at the end of the build, listing
 * every module's finding. Two modules, because the point is that Lint still runs in the second
 * module after the first one found something.
 */
internal fun runAndroidLibraryNewApiFailsCheck(row: Map<String, String>, tempDir: Path) {
    val dir = tempDir.resolve(row.getValue("id") + "-newapi")
    val writeProject = { projectDir: Path ->
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
        // An ignored variant disables that variant's Lint tasks; the root report merge must
        // still build its task graph and keep the other variant's findings.
        second.resolve("build.gradle.kts").writeText(
            kotlinAndroid + "fkcSetupAndroidLibrary(namespace = \"compat.second\", " +
                "config = { noVerificationBuildTypes = listOf(\"release\") })\n",
        )
    }
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-android-library-newapi",
        projectDir = dir,
        tasks = listOf(CHECK_TASK),
        forbiddenOutput = ANDROID_NOISE,
        expectFailure = listOf(
            "Android Lint found 2 calls above minSdk (NewApi)",
            "src/main/kotlin/compat/RootNewApi.kt",
            "second/src/main/kotlin/compat/SecondNewApi.kt",
        ),
        writeProject = writeProject,
    )
    runJdkOnlyApiFailsCase(row, tempDir, dir, "src/main/kotlin/compat", writeProject = writeProject)
}

/**
 * Android code runs against the device's API (`android.jar`), never the JDK's. KGP hides the JDK
 * only on AGP 8's `kotlin-android` path, so elsewhere a call to JDK-only API compiled and then
 * failed on a device. Main code must not see the JDK on any Android path.
 */
private fun runJdkOnlyApiFailsCase(
    row: Map<String, String>,
    tempDir: Path,
    dir: Path,
    sourceDir: String,
    arguments: List<String> = emptyList(),
    writeProject: (Path) -> Unit,
) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-android-jdk-only-api",
        projectDir = dir,
        tasks = listOf("assemble"),
        arguments = arguments,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
        expectFailure = listOf("Unresolved reference 'constant'"),
    ) {
        writeProject(it)
        // `java.lang.constant` (JDK 12) is not part of the Android API.
        it.resolve(sourceDir).resolve("JdkOnly.kt").writeText(
            "package compat\n\nfun jdkOnly(): Any = java.lang.constant.ClassDesc.of(\"x\")\n",
        )
    }
}

/** The KMP Android paths name their Lint tasks differently; their reports must reach the check. */
internal fun runKmpNewApiFailsCheck(row: Map<String, String>, tempDir: Path, buildScript: String) {
    val dir = tempDir.resolve(row.getValue("id") + "-newapi")
    val arguments = listOf("-PKMP_TARGETS=ANDROID")
    val writeProject = { projectDir: Path ->
        // ABI validation would fail `check` first on the missing API dump; it is not the subject.
        projectDir.resolve("build.gradle.kts").writeText(
            buildScript.replace("enableApiValidation = true", "enableApiValidation = false"),
        )
        writeAndroidLintConfig(projectDir)
        writeNewApiCall(projectDir.resolve("src/androidMain/kotlin/compat"), "KmpNewApi")
    }
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-kmp-newapi",
        projectDir = dir,
        tasks = listOf(CHECK_TASK),
        arguments = arguments,
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
        expectFailure = listOf(
            "Android Lint found 1 call above minSdk (NewApi)",
            "src/androidMain/kotlin/compat/KmpNewApi.kt",
        ),
        writeProject = writeProject,
    )
    runJdkOnlyApiFailsCase(
        row,
        tempDir,
        dir,
        sourceDir = "src/androidMain/kotlin/compat",
        arguments = arguments,
        writeProject = writeProject,
    )
}
