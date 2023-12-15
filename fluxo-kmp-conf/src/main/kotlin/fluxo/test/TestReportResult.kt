package fluxo.test

import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.jetbrains.kotlin.gradle.tasks.KotlinTest

internal class TestReportResult
private constructor(
    val result: TestResult,
    val className: String,
    val testSuite: String,
    val kmpTarget: String?,
    val name: String,
) {
    companion object {
        fun from(
            task: AbstractTestTask,
            desc: TestDescriptor,
            result: TestResult,
        ): TestReportResult {
            val className = desc.className.orEmpty()
            val testSuite = ":${task.project.name} $className"
            val testTaskName = task.name.substringBeforeLast("Test")
            val kmpTarget: String?

            var name = desc.displayName.orEmpty()
            if (!name.endsWith(']')) {
                val targetName = (task as? KotlinTest)?.targetName ?: testTaskName
                if (targetName.isNotBlank()) {
                    name += "[$targetName]"
                }
            }

            kmpTarget = name.substringAfterLast('[', "").trimEnd(']').takeIf { it.isNotEmpty() }

            // Show target details in test name (browser/node, background, and so on.)
            if (kmpTarget != testTaskName && ", " !in name) {
                name = name.substringBeforeLast('[') + "[$testTaskName]"
            }

            return TestReportResult(
                result = result,
                className = className,
                testSuite = testSuite,
                kmpTarget = kmpTarget,
                name = name,
            )
        }
    }
}
