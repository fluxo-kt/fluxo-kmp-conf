package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

internal class ComposeCompatibilityTestKitSmokeTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    @TestFactory
    fun generatedComposeDesktopConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("compose-desktop", "compose-desktop-preapplied").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runComposeDesktopConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedComposeKmpAndroidConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("compose-kmp-agp8", "compose-kmp-agp9").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runComposeKmpAndroidConsumer(row, tempDir)
            }
        }
}
