package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

internal class GradlePluginCompatibilityTestKitSmokeTest {

    // NEVER is required: the inner build's TestKit daemon keeps gradleUserHome (under this dir)
    // open, so ON_SUCCESS/ALWAYS cleanup throws "Failed to delete temp directory". CI is ephemeral.
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    @TestFactory
    fun generatedGradlePluginConsumersPublishTheRequestedPluginId(): Iterable<DynamicTest> =
        selectedRows(fixture = "gradle-plugin").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runGradlePluginConsumer(row, tempDir)
            }
        }
}
