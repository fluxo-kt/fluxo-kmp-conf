package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/**
 * The non-KMP `com.android.library` path. Its own class (and CI leg) apart from the KMP Android
 * path: together they outran one leg's time budget.
 */
internal class AndroidLibraryCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedAndroidLibraryConsumersUseLegacyAndroidPath(): Iterable<DynamicTest> =
        selectedRows(
            "android-lib-agp8",
            "android-lib-agp8-exec",
            "android-lib-agp9",
            "android-lib-agp9-exec",
        ).map { row ->
            compatTest(row.getValue("id")) {
                runAndroidLibraryConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedAndroidLibraryNewApiFailsCheckAtBuildEnd(): Iterable<DynamicTest> =
        selectedRows("android-lib-agp8-exec", "android-lib-agp9-exec").map { row ->
            compatTest(row.getValue("id")) {
                runAndroidLibraryNewApiFailsCheck(row, tempDir)
            }
        }
}
