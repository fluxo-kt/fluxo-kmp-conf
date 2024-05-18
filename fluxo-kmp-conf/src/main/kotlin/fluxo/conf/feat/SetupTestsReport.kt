@file:Suppress("ArgumentListWrapping", "Wrapping")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.impl.closureOf
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.register
import fluxo.conf.impl.splitCamelCase
import fluxo.conf.impl.withType
import fluxo.log.e
import fluxo.log.l
import fluxo.test.TestReportResult
import fluxo.test.TestReportService
import fluxo.test.TestReportsMergeTask
import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.internal.tasks.JvmConstants.TEST_TASK_NAME
import org.gradle.api.tasks.testing.AbstractTestTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.language.base.plugins.LifecycleBasePlugin.BUILD_TASK_NAME
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest
import org.jetbrains.kotlin.gradle.testing.internal.KotlinTestReport
import org.jetbrains.kotlin.konan.target.Family

private const val TEST_REPORTS_TASK_NAME = "mergedTestReport"
private const val TEST_REPORTS_FILE_NAME = "tests-report-merged.xml"

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FluxoKmpConfContext.setupTestsReport() {
    val project = rootProject

    val mergedReportService = when {
        testsDisabled -> null
        else -> try {
            project.gradle.sharedServices.registerIfAbsent(
                TestReportService.NAME, TestReportService::class.java,
            ) {
                // TODO: Support Gradle before 8.0 (set service parameter and usesService) ?
            }
        } catch (e: Throwable) {
            project.logger.e("Failed to register TestReportService: $e", e)
            null
        }
    }

    val mergedReportTask = when {
        testsDisabled || mergedReportService == null -> null
        else -> {
            project.logger.l("setupTestsReport, register :$TEST_REPORTS_TASK_NAME task")
            project.tasks.register<TestReportsMergeTask>(TEST_REPORTS_TASK_NAME) {
                output.set(project.layout.buildDirectory.file(TEST_REPORTS_FILE_NAME))
            }
        }
    }

    project.allprojects {
        if (mergedReportTask != null) {
            val finalizedByReport = Action<Task> { finalizedBy(mergedReportTask) }
            tasks.namedCompat { it in COMMON_TEST_TASKS_NAMES }
                .configureEach(finalizedByReport)

            try {
                tasks.withType(KotlinTestReport::class.java, finalizedByReport)
            } catch (e: Throwable) {
                logger.e("Failed to configure KotlinTestReport tasks: $e", e)
            }
        }

        val projectName = name
        tasks.withType<AbstractTestTask> configuration@{
            if (!enabled || mergedReportTask == null || !isTestTaskAllowed()) {
                disableTask()
                return@configuration
            }

            val testTask = this
            finalizedBy(mergedReportTask)
            mergedReportTask.configure { mustRunAfter(testTask) }

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

            // For release builds, we want to fail on any test failure.
            if (isRelease) {
                ignoreFailures = false
            }

            // Always run all available tests on CI
            if (isCI && this is Test) {
                failFast = false
            }

            val rootLogger = rootProject.logger
            afterTest(
                closureOf { desc: TestDescriptor, result: TestResult ->
                    mergedReportService?.orNull?.registerTestResult(
                        TestReportResult.from(testTask, desc, result, projectName),
                        rootLogger,
                    )
                },
            )
        }
    }
}


private val COMMON_TEST_TASKS_NAMES: Set<String> = hashSetOf(
    CHECK_TASK_NAME, BUILD_TASK_NAME, TEST_TASK_NAME,
    "allTests", "jvmTest", "jsTest", "jsNodeTest", "jsBrowserTest",
    "mingwX64Test",
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

    platform.equals("mingw", ignoreCase = true) ||
        platform.equals("win", ignoreCase = true) ||
        platform.equals("windows", ignoreCase = true)
    -> Family.MINGW

    else -> throw IllegalArgumentException("Unsupported family: $platform")
}

