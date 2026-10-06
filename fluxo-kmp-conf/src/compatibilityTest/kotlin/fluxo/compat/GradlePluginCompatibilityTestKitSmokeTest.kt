package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class GradlePluginCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedGradlePluginConsumersPublishTheRequestedPluginId(): Iterable<DynamicTest> =
        selectedRows("gradle-plugin").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runGradlePluginConsumer(row, tempDir)
            }
        }
}
