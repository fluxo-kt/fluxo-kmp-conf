package fluxo.compat

import java.nio.file.Path
import kotlin.io.path.writeText

internal fun runComposeDesktopConsumer(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id"))
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-compose-desktop-consumer",
        projectDir = projectDir,
    ) {
        it.resolve("build.gradle.kts").writeText(markerComposeDesktopBuildScript(row))
        writeComposeDesktopSources(it)
    }
    assertNoForbiddenResolvedClasspathLeaks(projectDir)
}

internal fun runComposeKmpAndroidConsumer(row: Map<String, String>, tempDir: Path) {
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-compose-kmp-android-consumer",
        arguments = listOf("-PKMP_TARGETS=ANDROID,JVM"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_LINT_VERSION_NOISE,
    ) { projectDir ->
        projectDir.resolve("build.gradle.kts").writeText(markerComposeKmpAndroidBuildScript(row))
        projectDir.resolve("gradle.properties").writeText("android.useAndroidX=true\n")
        writeComposeKmpSources(projectDir)
    }
}
