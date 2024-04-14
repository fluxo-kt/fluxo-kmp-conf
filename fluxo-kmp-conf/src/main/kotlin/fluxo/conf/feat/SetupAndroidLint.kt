package fluxo.conf.feat

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.LintTool
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import com.android.build.gradle.tasks.ExtractAnnotations
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.ANDROID_EXT_NAME
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.ifNotEmpty
import fluxo.conf.impl.withType
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.l
import fluxo.log.v
import fluxo.log.w
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import java.util.concurrent.atomic.AtomicBoolean
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Property
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// TODO: Setup Lint for NonAndroid projects
//  https://github.com/JetBrains/compose-multiplatform-core/blob/3b8ba3c/buildSrc/private/src/main/kotlin/androidx/build/LintConfiguration.kt
//  https://github.com/JetBrains/compose-multiplatform-core/blob/c366505/buildSrc/private/src/main/kotlin/androidx/build/AndroidXImplPlugin.kt#L591
//  https://github.com/androidx/androidx/blob/8cc7a40/buildSrc/private/src/main/kotlin/androidx/build/LintConfiguration.kt#L49
//  https://github.com/slackhq/slack-gradle-plugin/blob/a9f12a9/slack-plugin/src/main/kotlin/slack/gradle/lint/LintTasks.kt#L105

private const val MERGE_LINT_TASK_NAME = "mergeLintSarif"

internal fun FluxoKmpConfContext.registerLintMergeRootTask(): TaskProvider<ReportMergeTask>? =
    registerReportMergeTask(
        name = MERGE_LINT_TASK_NAME,
        description = "Merges all Lint reports from all modules to the root one",
        filePrefix = "lint",
    )

internal fun Project.setupAndroidLint(
    conf: FluxoConfigurationExtensionImpl,
    ignoredBuildTypes: List<String>,
    ignoredFlavors: List<String>,
) {
    val context = conf.ctx
    val disableLint = context.testsDisabled || !context.isTargetEnabled(KmpTargetCode.ANDROID)
    configureExtension(ANDROID_EXT_NAME, CommonExtension::class) {
        lint {
            if (SHOW_DEBUG_LOGS) {
                logger.v("Setup Android Lint (disable=$disableLint)")
            }

            sarifReport = !disableLint
            htmlReport = !disableLint
            textReport = !disableLint
            xmlReport = false

            // Use baseline only for CI checks, show all problems in local development.
            // Don't use if file doesn't exist, and we're running the `check` task.
            if (context.isCI || context.isRelease) {
                val file = file("lint-baseline.xml")
                if (file.exists() || CHECK_TASK_NAME !in context.startTaskNames) {
                    baseline = file
                }
            }

            abortOnError = false
            absolutePaths = false
            checkAllWarnings = !disableLint
            checkDependencies = false
            checkReleaseBuilds = !disableLint
            explainIssues = false
            noLines = true
            warningsAsErrors = !disableLint
        }
    }

    if (!disableLint) {
        val mergeLintTask = context.mergeLintTask
        tasks.withType<AndroidLintTask> {
            reportLintVersion()
            val taskName = name
            if (mergeLintTask != null && taskName.startsWith("lintReport")) {
                if (SHOW_DEBUG_LOGS) {
                    logger.v("Setup merging of sarif Lint reports for task $taskName")
                }
                val lintTask = this
                mergeLintTask.configure {
                    input.from(lintTask.sarifReportOutputFile)
                    dependsOn(lintTask)
                }
            }
        }
    }

    val variants = (ignoredBuildTypes + ignoredFlavors)
        .ifNotEmpty { toHashSet().toTypedArray() }
    if (variants.isNullOrEmpty()) {
        tasks.withType<AndroidLintAnalysisTask> { reportLintVersion() }
        return
    }
    val disableIgnoredVariants: AndroidVariantTask.() -> Unit = {
        reportLintVersion()
        if (enabled) {
            for (v in variants) {
                if (name.contains(v, ignoreCase = true)) {
                    disableTask()
                    break
                }
            }
        }
    }
    tasks.withType<AndroidLintTextOutputTask>(disableIgnoredVariants)
    tasks.withType<AndroidLintAnalysisTask>(disableIgnoredVariants)
    tasks.withType<AndroidLintTask>(disableIgnoredVariants)
}

private fun Task.reportLintVersion() {
    if (IS_VERSION_REPORTED.get()) {
        return
    }

    // Android Lint version reporting
    //
    // Version can be altered in runtime,
    // so we need to get the actual loaded version from the task.
    // https://vadzimv.dev/2021/07/28/calculate-lint-version.html
    // https://mvnrepository.com/artifact/com.android.tools.lint/lint
    /** @see com.android.build.gradle.options.StringOption.LINT_VERSION_OVERRIDE */

    val lintTool = when (this) {
        is AndroidLintTask -> lintTool
        is AndroidLintAnalysisTask -> lintTool
        is ExtractAnnotations -> lintTool
        else -> null
    } ?: return

    // Usually, the version is available only right before the task execution.
    if (!reportLintVersion(lintTool)) {
        doFirst {
            reportLintVersion(lintTool)
        }
    }
}

@Suppress("NestedBlockDepth", "ReturnCount")
private fun Task.reportLintVersion(lintTool: LintTool?): Boolean {
    var lintVersion: String? = null
    var versionKey: Property<String>? = null
    try {
        versionKey = lintTool?.versionKey
        lintVersion = versionKey?.orNull?.trim()
    } catch (e: Throwable) {
        var report = true
        try {
            if (versionKey == null && lintTool != null) {
                // AGP before 8.2
                lintTool.javaClass.getDeclaredMethod("getVersion").invoke(lintTool)?.let {
                    lintVersion = it.toString().trim()
                }
                report = false
            }
        } catch (e2: Throwable) {
            e2.addSuppressed(e)
            logger.w("Failed to get Android Lint version", e2)
            return false
        }
        if (report) {
            logger.w("Failed to get Android Lint version", e)
            return false
        }
    }
    if (lintVersion.isNullOrEmpty() || !IS_VERSION_REPORTED.compareAndSet(false, true)) {
        return false
    }
    logger.l("Android Lint version: $lintVersion")
    return true
}

private val IS_VERSION_REPORTED = AtomicBoolean()

