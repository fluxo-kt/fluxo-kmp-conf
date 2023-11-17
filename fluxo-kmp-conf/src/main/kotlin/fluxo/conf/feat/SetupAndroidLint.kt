package fluxo.conf.feat

import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.tasks.AndroidVariantTask
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.ifNotEmpty
import fluxo.conf.impl.withType
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

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
    val context = conf.context
    val disableLint = context.testsDisabled || !context.isTargetEnabled(KmpTargetCode.ANDROID)
    configureExtension("android", CommonExtension::class) {
        lint {
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
        context.mergeLintTask?.let { mergeLint ->
            tasks.withType<AndroidLintTask> {
                val lintTask = this
                if (name.startsWith("lintReport")) {
                    mergeLint.configure {
                        input.from(lintTask.sarifReportOutputFile)
                        dependsOn(lintTask)
                    }
                }
            }
        }
    }

    val variants = (ignoredBuildTypes + ignoredFlavors)
        .ifNotEmpty { toHashSet().toTypedArray() }
    if (variants.isNullOrEmpty()) {
        return
    }
    val disableIgnoredVariants: AndroidVariantTask.() -> Unit = {
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
