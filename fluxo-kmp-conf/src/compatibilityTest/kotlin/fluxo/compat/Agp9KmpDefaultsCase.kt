package fluxo.compat

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

/**
 * AGP 9's KMP Android plugin packages no Android resources and creates no host (unit) tests
 * unless asked, so moving a module from AGP 8 dropped both silently. A module with resources and
 * tests, which doesn't ask, must get both; the base fixture asks itself, so a second request
 * (which AGP rejects) is covered there.
 */
internal fun runAgp9KmpResourcesAndHostTestsCase(row: Map<String, String>, tempDir: Path) {
    val projectDir = tempDir.resolve(row.getValue("id") + "-defaults")
    runConsumerCase(
        row,
        tempDir,
        rootProjectName = "compat-agp9-kmp-defaults",
        projectDir = projectDir,
        tasks = listOf("assemble", "testAndroidHostTest"),
        arguments = listOf("-PKMP_TARGETS=ANDROID"),
        forbiddenOutput = KMP_NO_TARGET_DIAGNOSTICS + ANDROID_NOISE,
    ) {
        it.resolve("build.gradle.kts").writeText(
            markerAgp9KmpBuildScript(row).replace(".withHostTest {}", ".let {}") +
                "\nproject.extensions.configure<" +
                "org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension>(\"kotlin\") {\n" +
                "    sourceSets.getByName(\"commonTest\").dependencies " +
                "{ implementation(kotlin(\"test\")) }\n}\n",
        )
        writeAndroidKmpSources(it)
        val values = it.resolve("src/androidMain/res/values")
        Files.createDirectories(values)
        values.resolve("strings.xml").writeText(
            "<resources>\n    <string name=\"compat_name\">compat</string>\n</resources>\n",
        )
        val test = it.resolve("src/commonTest/kotlin/compat")
        Files.createDirectories(test)
        test.resolve("SubjectTest.kt").writeText(
            "package compat\n\nimport kotlin.test.Test\n\nclass SubjectTest {\n" +
                "    @Test\n    fun runs() = Unit\n}\n",
        )
    }
    val aar = projectDir.resolve("build/outputs/aar").toFile().listFiles().orEmpty()
        .single { it.extension == "aar" }
    val packaged = java.util.zip.ZipFile(aar).use { zip -> zip.getEntry("res/values/values.xml") }
    check(packaged != null) { "No Android resources in $aar" }
    val results = projectDir.resolve("build/test-results/testAndroidHostTest").toFile()
    check(results.listFiles().orEmpty().any { it.name.endsWith(".xml") }) {
        "Host tests did not run: no results in $results"
    }
}
