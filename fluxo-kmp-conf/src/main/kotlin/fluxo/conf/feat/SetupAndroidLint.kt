package fluxo.conf.feat

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import com.android.build.gradle.internal.lint.AndroidLintAnalysisTask
import com.android.build.gradle.internal.lint.AndroidLintTask
import com.android.build.gradle.internal.lint.AndroidLintTextOutputTask
import com.android.build.gradle.internal.lint.LINT_XML_CONFIG_FILE_NAME
import com.android.build.gradle.internal.lint.LintModelWriterTask
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

private const val MERGE_LINT_TASK_NAME = "mergeLintSarif"
private const val BASELINE_LINT_TASK_NAME = "updateLintBaseline"

private const val BASELINE_FILE_NAME = "lint-baseline.xml"

private val CONFIG_FILE_NAMES = arrayOf(
    "$CONFIG_DIR_NAME/$LINT_XML_CONFIG_FILE_NAME",
    "$CONFIG_DIR_NAME/lint/$LINT_XML_CONFIG_FILE_NAME",
    LINT_XML_CONFIG_FILE_NAME, // "lint.xml"
)

private val VERSION_CHECKS = arrayOf(
    "GradleDependency",
    "NewerVersionAvailable",
)

internal fun FluxoKmpConfContext.registerLintMergeRootTask(): TaskProvider<ReportMergeTask>? =
    registerReportMergeTask(
        name = MERGE_LINT_TASK_NAME,
        description = "Merges all Lint reports from all modules to the root one",
        filePrefix = "lint",
    )

internal fun Project.setupAndroidLint(
    conf: FluxoConfigurationExtensionImpl,
    ignoredBuildTypes: List<String> = emptyList(),
    ignoredFlavors: List<String> = emptyList(),
    testsDisabled: Boolean = !conf.setupVerification || conf.ctx.testsDisabled,
    notForAndroid: Boolean = false,
) {
    val ctx = conf.ctx
    val disableLint = testsDisabled || !notForAndroid && !ctx.isTargetEnabled(KmpTargetCode.ANDROID)
    val isBaselineRequested = conf.ctx.hasStartTaskCalled(BASELINE_LINT_TASK_NAME)
    configureAndroidLintExtension(conf, disableLint, isBaselineRequested, notForAndroid)
    if (disableLint) {
        return
    }

    val variants = (ignoredBuildTypes + ignoredFlavors)
        .ifNotEmpty { toHashSet().toTypedArray() }
    val disableIgnoredVariants: AndroidVariantTask.() -> Unit = {
        reportLintVersion()
        if (enabled && !variants.isNullOrEmpty()) {
            for (v in variants) {
                // TODO: Check variantName instead of the task name?
                if (name.contains(v, ignoreCase = true)) {
                    disableTask()
                    break
                }
            }
        }
    }

    configureAndroidLintTasks(conf, disableIgnoredVariants)

    if (variants.isNullOrEmpty()) {
        tasks.withType<AndroidLintAnalysisTask> { reportLintVersion() }
        return
    }
    tasks.withType<AndroidLintTextOutputTask>(disableIgnoredVariants)
    tasks.withType<AndroidLintAnalysisTask>(disableIgnoredVariants)
    tasks.withType<LintModelWriterTask>(disableIgnoredVariants)
    // tasks.withType<AndroidLintTask>(disableIgnoredVariants) // Already configured
}

private fun Project.configureAndroidLintTasks(
    conf: FluxoConfigurationExtensionImpl,
    disableIgnoredVariants: AndroidVariantTask.() -> Unit,
) = tasks.withType<AndroidLintTask> {
    disableIgnoredVariants()

    val taskName = name
    val mergeLintTask = conf.ctx.mergeLintTask
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

    if (conf.kotlinConfig.k2) {
        try {
            useK2Uast.set(true)
        } catch (_: Throwable) {
        }
    }
}

private fun Project.configureAndroidLintExtension(
    conf: FluxoConfigurationExtensionImpl,
    disableLint: Boolean,
    isBaselineRequested: Boolean,
    notForAndroid: Boolean,
) {
    val lintConfiguration: Lint.() -> Unit = {
        configureAndroidLintExtension(
            conf = conf,
            disableLint = disableLint,
            reBaseline = isBaselineRequested,
        )
    }
    if (notForAndroid) {
        configureExtension(name = LINT_EXTENSION_NAME, action = lintConfiguration)
    } else {
        configureExtension(name = ANDROID_EXT_NAME, CommonExtension::class) {
            lint(lintConfiguration)
        }
    }
}

@Suppress("CyclomaticComplexMethod")
internal fun Lint.configureAndroidLintExtension(
    conf: FluxoConfigurationExtensionImpl,
    disableLint: Boolean,
    reBaseline: Boolean,
) {
    val p = conf.project

    if (SHOW_DEBUG_LOGS) {
        p.logger.v("Setup Android Lint (off=$disableLint, baseline=$reBaseline)")
    }

    sarifReport = !disableLint
    htmlReport = !disableLint
    textReport = !disableLint
    xmlReport = false

    // Use baseline only for CI checks, show all problems in local development.
    // Don't use if file doesn't exist, and we're running the `check` task.
    val ctx = conf.ctx
    val rootProjectDir = p.rootProject.layout.projectDirectory
    var hasBaseline = false
    if (reBaseline || ctx.isCI || ctx.isRelease) {
        val baselineFile = p.layout.projectDirectory.file(BASELINE_FILE_NAME).asFile
        if (reBaseline || baselineFile.exists() || CHECK_TASK_NAME !in ctx.startTaskNames) {
            hasBaseline = true
            baseline = baselineFile
        }
    }

    // TODO: Cache the Lint configuration file?
    var hasConfig = false
    for (name in CONFIG_FILE_NAMES) {
        val confFile = rootProjectDir.file(name).asFile
        if (confFile.exists()) {
            hasConfig = true
            lintConfig = confFile
            break
        }
    }
    if (!hasConfig) {
        if (!reBaseline) {
            informational += VERSION_CHECKS
        }
        // fatal += "KotlincFE10"
        // disable += "UnknownIssueId"
    }
    if (hasBaseline) {
        disable += "LintBaseline"
    }
    if (reBaseline) {
        disable += VERSION_CHECKS
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


/** @see com.android.build.api.dsl.Lint */
private const val LINT_EXTENSION_NAME = "lint"
