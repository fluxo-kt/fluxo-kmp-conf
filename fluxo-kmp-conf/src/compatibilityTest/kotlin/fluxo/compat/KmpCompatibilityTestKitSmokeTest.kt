package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class KmpCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedKmpJvmFilteredConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("kmp").flatMap { row ->
            kmpConsumerCases(row, tempDir).map { (case, run) ->
                compatTest("${row.getValue("id")} $case", run)
            }
        }

    @TestFactory
    fun generatedKmpCommonOnlyConsumersCreateNoPlatformTargets(): Iterable<DynamicTest> =
        selectedRows("kmp-common").map { row ->
            compatTest(row.getValue("id")) {
                runKmpCommonOnlyConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKmpConsumersRejectInvalidTargetFilters(): Iterable<DynamicTest> =
        selectedRows("kmp-invalid-target").map { row ->
            compatTest(row.getValue("id")) {
                runKmpInvalidTargetConsumer(row, tempDir)
            }
        }
}
