package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.io.CleanupMode
import org.junit.jupiter.api.io.TempDir

internal class KotlinJvmCompatibilityTestKitSmokeTest {

    // NEVER is required: the inner build's TestKit daemon keeps gradleUserHome (under this dir)
    // open, so ON_SUCCESS/ALWAYS cleanup throws "Failed to delete temp directory". CI is ephemeral.
    @TempDir(cleanup = CleanupMode.NEVER)
    lateinit var tempDir: Path

    @TestFactory
    fun generatedKotlinJvmConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows(fixture = "kotlin-jvm").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKotlinJvmConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKotlinJvmMarkerConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows(fixture = "kotlin-jvm").map { row ->
            DynamicTest.dynamicTest("${row.getValue("id")}-marker") {
                runKotlinJvmMarkerConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKotlinJvmConsumersHonorDisabledTests(): Iterable<DynamicTest> =
        selectedRows(fixture = "kotlin-jvm-tests-disabled").map { row ->
            DynamicTest.dynamicTest(row.getValue("id")) {
                runKotlinJvmTestsDisabledConsumer(row, tempDir)
            }
        }
}
