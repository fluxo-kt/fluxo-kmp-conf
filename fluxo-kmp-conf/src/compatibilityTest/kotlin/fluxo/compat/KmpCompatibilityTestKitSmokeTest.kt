package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

internal class KmpCompatibilityTestKitSmokeTest {

    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    @TestFactory
    fun generatedKmpJvmFilteredConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows(fixture = "kmp").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKmpConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKmpCommonOnlyConsumersCreateNoPlatformTargets(): Iterable<DynamicTest> =
        selectedRows(fixture = "kmp-common").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKmpCommonOnlyConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKmpConsumersRejectInvalidTargetFilters(): Iterable<DynamicTest> =
        selectedRows(fixture = "kmp-invalid-target").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKmpInvalidTargetConsumer(row, tempDir)
            }
        }
}
