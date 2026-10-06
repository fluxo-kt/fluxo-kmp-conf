package fluxo.compat

import java.nio.file.Path
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

internal class KotlinJvmCompatibilityTestKitSmokeTest {

    private val tempDir: Path = newCompatProjectsDir()

    @TestFactory
    fun generatedKotlinJvmConsumersRunRequiredLifecycleTasks(): Iterable<DynamicTest> =
        selectedRows(fixture = "kotlin-jvm").flatMap { row ->
            kotlinJvmConsumerCases(row, tempDir).map { (case, run) ->
                DynamicTest.dynamicTest("${row.getValue("id")} $case", run)
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
