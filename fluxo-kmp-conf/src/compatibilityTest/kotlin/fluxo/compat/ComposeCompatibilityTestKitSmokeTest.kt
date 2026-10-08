package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class ComposeCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedComposeDesktopConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("compose-desktop", "compose-desktop-preapplied").map { row ->
            compatTest(row.getValue("id")) {
                runComposeDesktopConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedComposeKmpAndroidConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("compose-kmp-agp8", "compose-kmp-agp9").map { row ->
            compatTest(row.getValue("id")) {
                runComposeKmpAndroidConsumer(row, tempDir)
            }
        }
}
