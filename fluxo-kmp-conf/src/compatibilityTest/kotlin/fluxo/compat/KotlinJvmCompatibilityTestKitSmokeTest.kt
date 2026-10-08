package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class KotlinJvmCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedKotlinJvmConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("kotlin-jvm").flatMap { row ->
            kotlinJvmConsumerCases(row, tempDir).map { (case, run) ->
                compatTest("${row.getValue("id")} $case", run)
            }
        }

    @TestFactory
    fun generatedKotlinJvmMarkerConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows("kotlin-jvm").map { row ->
            compatTest("${row.getValue("id")}-marker") {
                runKotlinJvmMarkerConsumer(row, tempDir)
            }
        }

    @TestFactory
    fun generatedKotlinJvmConsumersHonorDisabledTests(): Iterable<DynamicTest> =
        selectedRows("kotlin-jvm-tests-disabled").map { row ->
            compatTest(row.getValue("id")) {
                runKotlinJvmTestsDisabledConsumer(row, tempDir)
            }
        }
}
