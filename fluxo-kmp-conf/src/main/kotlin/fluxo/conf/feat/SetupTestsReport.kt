@file:Suppress("ArgumentListWrapping", "Wrapping")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.TestsReportsMergeTask
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.impl.closureOf
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.e
import fluxo.conf.impl.register
import fluxo.conf.impl.splitCamelCase
import fluxo.conf.impl.withType
import org.gradle.api.internal.tasks.JvmConstants.TEST_TASK_NAME
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.language.base.plugins.LifecycleBasePlugin.ASSEMBLE_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport
import org.jetbrains.kotlin.konan.target.Family

private const val TEST_REPORTS_TASK_NAME = "mergedTestReport"
private const val TEST_REPORTS_FILE_NAME = "tests-report-merged.xml"

internal fun FluxoKmpConfContext.setupTestsReport() {
    val project = rootProject
    val mergedReport = if (testsDisabled) null else
        project.tasks.register<TestsReportsMergeTask>(TEST_REPORTS_TASK_NAME) {
            group = JavaBasePlugin.VERIFICATION_GROUP
            description = "Combines all tests reports from all modules to the published root one"
            output.set(project.layout.buildDirectory.file(TEST_REPORTS_FILE_NAME))
        }

    project.allprojects {
        if (mergedReport != null) {
            tasks.matching { it.name in COMMON_TEST_TASKS_NAMES }
                .configureEach { finalizedBy(mergedReport) }

            try {
                tasks.withType<KotlinTestReport> { finalizedBy(mergedReport) }
            } catch (e: Throwable) {
                logger.e("Failed to configure KotlinTestReport tasks: $e", e)
            }
        }

        tasks.withType<AbstractTestTask> configuration@{
            if (!enabled || mergedReport == null || !isTestTaskAllowed()) {
                disableTask()
                return@configuration
            }

            val testTask = this
            finalizedBy(mergedReport)
            mergedReport.configure { mustRunAfter(testTask) }

            testLogging {
                events = setOf(
                    TestLogEvent.FAILED,
                    TestLogEvent.SKIPPED,
                    TestLogEvent.STANDARD_OUT,
                    TestLogEvent.STANDARD_ERROR,
                )
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showCauses = true
                showStackTraces = true
            }

            ignoreFailures = isCI // Always run all tests for all modules on CI

            afterTest(
                closureOf { desc: TestDescriptor, result: TestResult ->
                    mergedReport.get().registerTestResult(testTask, desc, result)
                },
            )
        }
    }
}


private val COMMON_TEST_TASKS_NAMES: Set<String> = hashSetOf(
    CHECK_TASK_NAME, ASSEMBLE_TASK_NAME, BUILD_TASK_NAME, TEST_TASK_NAME,
    "allTests", "jvmTest", "jsTest", "jsNodeTest", "jsBrowserTest", "mingwX64Test",
)


context(FluxoKmpConfContext)
internal fun AbstractTestTask.isTestTaskAllowed(): Boolean {
    val isAllowed = when (this) {
        is KotlinJsTest -> isTargetEnabled(KmpTargetCode.JS)

        is KotlinNativeTest ->
            nativeFamilyFromString(platformFromTaskName(name)).isCompilationAllowed()

        // JVM/Android tests
        else -> isTargetEnabled(KmpTargetCode.JVM) || isTargetEnabled(KmpTargetCode.ANDROID)
    }
    if (!isAllowed) {
        logger.e("Unexpected test task $name! Target should be disabled")
    }
    return isAllowed
}

context(FluxoKmpConfContext)
private fun Family.isCompilationAllowed(): Boolean {
    return KmpTargetCode.fromKotlinFamily(this).any(::isTargetEnabled)
}

private fun platformFromTaskName(name: String): String? =
    name.splitCamelCase(limit = 2).firstOrNull()

@Suppress("CyclomaticComplexMethod")
private fun nativeFamilyFromString(platform: String?): Family = when {
    platform.equals("watchos", ignoreCase = true) -> Family.WATCHOS
    platform.equals("tvos", ignoreCase = true) -> Family.TVOS
    platform.equals("ios", ignoreCase = true) -> Family.IOS

    platform.equals("darwin", ignoreCase = true) ||
        platform.equals("apple", ignoreCase = true) ||
        platform.equals("macos", ignoreCase = true)
    -> Family.OSX

    platform.equals("android", ignoreCase = true) -> Family.ANDROID
    platform.equals("linux", ignoreCase = true) -> Family.LINUX
    platform.equals("wasm", ignoreCase = true) -> Family.WASM

    platform.equals("mingw", ignoreCase = true) ||
        platform.equals("win", ignoreCase = true) ||
        platform.equals("windows", ignoreCase = true)
    -> Family.MINGW

    else -> throw IllegalArgumentException("Unsupported family: $platform")
}

