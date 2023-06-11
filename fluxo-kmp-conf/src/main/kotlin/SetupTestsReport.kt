@file:Suppress("ArgumentListWrapping", "Wrapping")

import fluxo.conf.TestsReportsMergeTask
import impl.checkIsRootProject
import impl.closureOf
import impl.register
import impl.withType
import org.gradle.api.Project
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent

private const val TEST_REPORTS_TASK_NAME = "mergedTestReport"

public fun Project.setupTestsReport() {
    checkIsRootProject("setupTestsReport")

    val mergedReport = tasks.register<TestsReportsMergeTask>(TEST_REPORTS_TASK_NAME) {
        group = JavaBasePlugin.VERIFICATION_GROUP
        description = "Combines all tests reports from all modules to the published root one"
        output.set(project.layout.buildDirectory.file("tests-report-merged.xml"))
    }

    val disableTests by disableTests()
    allprojects {
        if (!disableTests) {
            val targetNames = hashSetOf(
                "check", "test", "allTests", "assemble", "build",
                "jvmTest", "jsTest", "jsNodeTest", "jsBrowserTest", "mingwX64Test",
            )
            tasks.matching { it.name in targetNames }.configureEach { t ->
                t.finalizedBy(mergedReport)
            }
        }

        tasks.withType<AbstractTestTask> configuration@{
            if (disableTests || !isTestTaskAllowed()) {
                enabled = false
                return@configuration
            }

            val testTask = this
            finalizedBy(mergedReport)

            if (enabled) {
                mergedReport.configure {
                    mustRunAfter(testTask)
                }
            }

            testLogging {
                it.events = setOf(
                    TestLogEvent.FAILED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_OUT,
                    TestLogEvent.STANDARD_ERROR,
                )
                it.exceptionFormat = TestExceptionFormat.FULL
                it.showExceptions = true
                it.showCauses = true
                it.showStackTraces = true
            }

            ignoreFailures = true // Always run all tests for all modules

            afterTest(
                closureOf { desc: TestDescriptor, result: TestResult ->
                    mergedReport.get().registerTestResult(testTask, desc, result)
                },
            )
        }
    }
}
