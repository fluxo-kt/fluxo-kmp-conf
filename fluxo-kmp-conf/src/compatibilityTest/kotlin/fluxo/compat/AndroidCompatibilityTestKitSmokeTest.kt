package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class AndroidCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedAgp8KmpConsumersUseLegacyAndroidPath(): Iterable<DynamicTest> =
        selectedRows("android-kmp-agp8", "android-kmp-agp8-exec").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAgp8KmpConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedAgp9KmpConsumersUseKmpAwareAndroidPath(): Iterable<DynamicTest> =
        selectedRows("android-kmp-agp9", "android-kmp-agp9-exec").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAgp9KmpConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedAgp9KmpAppConsumersFailWithMigrationGuidance(): Iterable<DynamicTest> =
        selectedRows(fixture = "android-kmp-agp9-app-unsupported").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAgp9KmpAppUnsupportedConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedAndroidLibraryConsumersUseLegacyAndroidPath(): Iterable<DynamicTest> =
        selectedRows(
            "android-lib-agp8",
            "android-lib-agp8-exec",
            "android-lib-agp9",
            "android-lib-agp9-exec",
        ).map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAndroidLibraryConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKmpNewApiFailsCheckAtBuildEnd(): Iterable<DynamicTest> =
        selectedRows("android-kmp-agp8-exec", "android-kmp-agp9-exec").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                val script = if (row.getValue("fixture") == "android-kmp-agp8-exec") {
                    markerAgp8KmpBuildScript(row)
                } else {
                    markerAgp9KmpBuildScript(row)
                }
                runKmpNewApiFailsCheck(row, tempDir, script)
            }
        }

    @TestFactory
    fun generatedAndroidLibraryNewApiFailsCheckAtBuildEnd(): Iterable<DynamicTest> =
        selectedRows("android-lib-agp8-exec", "android-lib-agp9-exec").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runAndroidLibraryNewApiFailsCheck(row, tempDir)
            }
        }
}
