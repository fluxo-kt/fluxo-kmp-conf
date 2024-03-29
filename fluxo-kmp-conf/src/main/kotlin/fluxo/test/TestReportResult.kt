package fluxo.test

import org.gradle.api.internal.tasks.testing.TestDescriptorInternal
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
            projectName: String,
        ): TestReportResult {
            val descInternal = desc as? TestDescriptorInternal
            if (descInternal?.isComposite == true) {
                print("")
            }

            val className = desc.className.let {
                if (it.isNullOrBlank()) desc.name else it
            }

            // Do not use project name from the task!
            // Invocation of 'Task.project' by a task at execution time is unsupported!
            val testSuite = ":$projectName $className"
            val testTaskName = task.name.substringBeforeLast("Test")

            var name = desc.displayName.orEmpty()
            if (!name.endsWith(']')) {
                val targetName = (task as? KotlinTest)?.targetName
                    ?: testTaskName.takeIf {
                        !it.endsWith("Verify") && !it.startsWith("processChain")
                    }
                if (!targetName.isNullOrBlank()) {
                    name += "[$targetName]"
                }
            }

            val hasKmpTarget = ']' in name
            val kmpTarget = when {
                !hasKmpTarget -> null
                else -> name.substringAfterLast('[', "")
                    .trimEnd(']')
                    .takeIf { it.isNotEmpty() }
            }

            // Show target details in test name (browser/node, background, and so on.)
            if (kmpTarget != null && kmpTarget != testTaskName && ", " !in name) {
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
