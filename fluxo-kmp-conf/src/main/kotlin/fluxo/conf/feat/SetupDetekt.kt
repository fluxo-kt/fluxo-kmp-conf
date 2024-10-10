package fluxo.conf.feat

import MAIN_SOURCE_SET_POSTFIX
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.MergeDetektBaselinesTask
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAndLog
import fluxo.conf.impl.android.DEBUG
import fluxo.conf.impl.android.RELEASE
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.dependencies
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.register
import fluxo.conf.impl.splitCamelCase
import fluxo.conf.impl.withType
import fluxo.log.e
import fluxo.log.l
import fluxo.vc.onLibrary
import io.github.detekt.gradle.DetektKotlinCompilerPlugin
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

private const val DEBUG_DETEKT_LOGS = false

private const val DETEKT_MAX_SUPPORTED_KOTLIN_VERSION = "2.1"

private const val MERGE_DETEKT_TASK_NAME = "mergeDetektSarif"

internal const val CONFIG_DIR_NAME = "config"

// https://detekt.dev/docs/introduction/reporting/#merging-reports
internal fun FluxoKmpConfContext.registerDetektMergeRootTask(): TaskProvider<ReportMergeTask>? =
    registerReportMergeTask(
        name = MERGE_DETEKT_TASK_NAME,
        description = "Merges all Detekt reports from all modules to the root one",
        filePrefix = "detekt",
    )

internal fun FluxoKmpConfContext.registerReportMergeTask(
    name: String,
    description: String,
    filePrefix: String,
): TaskProvider<ReportMergeTask>? {
    if (testsDisabled) return null
    return rootProject.tasks.register<ReportMergeTask>(name) {
        group = JavaBasePlugin.VERIFICATION_GROUP
        this.description = description
        output.set(project.layout.buildDirectory.file("$filePrefix-merged.sarif"))
    }
}

// TODO: Add option to ignore baselines completely and fail on anything,
//  to help working on reducing baselines.

// FIXME: Setup the "InvalidPackageDeclaration" rule for each module,
//  set the 'rootPackage' automatically from module group/package.
//  https://github.com/detekt/detekt/issues/4936#issue-1265233509

// FIXME: Setup checks for the non source set kotlin files (e.g., *.kts scripts).
//  See orbit-mvi setup for an example.

// FIXME: Setup the light-weight mode for the git hooks, to run only on the changed files.
//  And probably without types resolution.

// FIXME: Disable Detekt for the experimentalLatest test compilation.

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun Project.setupDetekt(
    conf: FluxoConfigurationExtensionImpl,
    ignoredBuildTypes: List<String>,
    ignoredFlavors: List<String>,
    testsDisabled: Boolean,
) {
    val context = conf.ctx
    if (!testsDisabled) {
        val asCompilerPlugin = conf.enableDetektCompilerPlugin == true
        logger.l("setup Detekt" + if (asCompilerPlugin) " as COMPILER PLUGIN" else "")

        // Detekt is always availabe in the classpath as an implementation dependency.
        pluginManager.apply(
            when (asCompilerPlugin) {
                true -> DetektKotlinCompilerPlugin::class.java
                else -> DetektPlugin::class.java
            },
        )
    }

    val detektBaselineFile = layout.projectDirectory.file(DETEKT_BASELINE_FILE_NAME)
    val mergeDetektBaselinesTask = when {
        testsDisabled || !context.hasStartTaskCalled(MergeDetektBaselinesTask.TASK_NAME) -> null
        else -> tasks.register<MergeDetektBaselinesTask>(MergeDetektBaselinesTask.TASK_NAME) {
            outputFile.set(detektBaselineFile)
        }
    }
    val detektMergeStarted = mergeDetektBaselinesTask != null
    val testStarted = context.startTaskNames.any { name ->
        TEST_TASK_PREFIXES.any { name.startsWith(it) }
    }

    val baselineIntermediateDir = project.layout.buildDirectory.dir("intermediates/detekt")
    configureExtensionIfAvailable<DetektExtension>(DetektPlugin.DETEKT_EXTENSION) {
        @Suppress("NAME_SHADOWING")
        val context = conf.ctx

        parallel = true
        buildUponDefaultConfig = true
        ignoreFailures = detektMergeStarted

        autoCorrect = conf.enableDetektAutoCorrect == true &&
            !context.isCI && !testStarted && !detektMergeStarted

        // For GitHub or another report consumers to know
        // where the file with issue is to place annotations correctly.
        val rootProjectDir = rootProject.layout.projectDirectory
        basePath = rootProjectDir.asFile.absolutePath

        this.ignoredBuildTypes = ignoredBuildTypes
        this.ignoredFlavors = ignoredFlavors

        // TODO: Cache the config dir?
        var configDir = rootProjectDir.dir(CONFIG_DIR_NAME)
            .takeIf { it.asFile.exists() } ?: rootProjectDir
        configDir = configDir.let {
            val detektDir = it.dir("detekt")
            if (detektDir.asFile.exists()) detektDir else it
        }

        val files = arrayListOf(
            layout.projectDirectory.file("detekt.yml"),
            configDir.file("detekt.yml"),
            configDir.file("detekt-formatting.yml"),
        )
        if (conf.kotlinConfig.setupCompose) {
            files += configDir.file("detekt-compose.yml")
        }
        files.retainAll {
            val f = it.asFile
            f.exists() && f.canRead()
        }
        if (files.isNotEmpty()) {
            @Suppress("SpreadOperator")
            config.from(*files.toTypedArray())
        }

        baseline = when {
            !detektMergeStarted -> detektBaselineFile
            else -> baselineIntermediateDir.get().file("$BASELINE.$EXT")
        }.asFile

        if (testsDisabled) {
            enableCompilerPlugin.set(false)
        }
    }

    val kc = conf.kotlinConfig
    val baselineTasks = tasks.withType<DetektCreateBaselineTask> {
        // FIXME: Use kotlin settings directly from the linked kotlin compilation task?

        kc.jvmTarget?.let { jvmTarget = it }

        val (lang) = kc.langAndApiVersions(isTest = false)
        lang?.let {
            val v = it.version
            val max = DETEKT_MAX_SUPPORTED_KOTLIN_VERSION
            languageVersion.set(if (v.toFloat() <= max.toFloat()) v else max)
        }

        if (mergeDetektBaselinesTask != null) {
            baseline.set(baselineIntermediateDir.map { it.file("$BASELINE-$name.$EXT") })
            finalizedBy(mergeDetektBaselinesTask)
        }
    }
    mergeDetektBaselinesTask?.configure {
        mustRunAfter(baselineTasks)
        baselineFiles.from(baselineTasks.map { it.baseline })
    }

    // FIXME: Disable non-resolving tasks if resolving version is available.
    val detektTasks = tasks.withType<Detekt> {
        val testsAreDisabled = context.testsDisabled
        val isDisabled = testsAreDisabled || !isDetektTaskAllowed(context)

        if (isDisabled) {
            if (enabled) {
                val reason = when {
                    testsAreDisabled -> "tests are disabled"
                    else -> "platform ${taskPlatform()} is disabled"
                }
                logger.e("Unexpected Detekt task {}, disabling as $reason", path)
                disableTask()
            }
        } else {
            // FIXME: Use kotlin settings directly from the linked kotlin compilation task?
            kc.jvmTarget?.let { jvmTarget = it }

            val (lang) = kc.langAndApiVersions(isTest = false)
            lang?.let {
                val v = it.version
                val max = DETEKT_MAX_SUPPORTED_KOTLIN_VERSION
                languageVersion = if (v.toFloat() <= max.toFloat()) v else max
            }

            if (DEBUG_DETEKT_LOGS) {
                debug = true
            }
        }

        reports.apply {
            sarif.required.set(!isDisabled)
            html.required.set(!isDisabled)
            txt.required.set(false)
            md.required.set(false)
            xml.required.set(false)
        }
    }

    if (!testsDisabled) {
        val detektAll = tasks.register<Task>("detektAll") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Calls all available Detekt tasks for this project"
            dependsOn(detektTasks)
        }
        tasks.namedCompat { it == CHECK_TASK_NAME }
            .configureEach { dependsOn(detektAll) }

        context.mergeDetektTask?.configure {
            dependsOn(detektTasks)
            input.from(detektTasks.map { it.sarifReportFile })
        }

        context.libs.run {
            dependencies {
                arrayOf(
                    "detekt-arrow",
                    "detekt-compiler",
                    "detekt-explicit",
                    "detekt-faire",
                    "detekt-formatting",
                    "detekt-hbmartin",
                    "detekt-ruleauthors",
                    "detekt-verify-implementation",
                ).forEach { id -> onLibrary(id) { detektPlugins(dh = this, it) } }

                if (kc.setupCompose) {
                    onLibrary("detekt-compose") { detektPlugins(dh = this, it) }
                }
                if (!conf.isApplication) {
                    onLibrary("detekt-libraries") { detektPlugins(dh = this, it) }
                }
            }
        }
    }
}


private fun Project.detektPlugins(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "detektPlugins", dependencyNotation)

private const val BASELINE = "baseline"

private const val EXT = "xml"

private const val DETEKT_BASELINE_FILE_NAME = "detekt-$BASELINE.$EXT"

private val TEST_TASK_PREFIXES = arrayOf(CHECK_TASK_NAME, TEST_TASK_NAME)


private fun Detekt.taskPlatform(): DetectedTaskPlatform {
    return getTaskDetailsFromName(name).platform
        ?: DetectedTaskPlatform.UNKNOWN
}

private fun Detekt.isDetektTaskAllowed(ctx: FluxoKmpConfContext): Boolean = with(ctx) {
    isTaskAllowed(getTaskDetailsFromName(name).platform)
}

private fun getTaskDetailsFromName(
    name: String,
    allowNonDetekt: Boolean = false,
): DetectedTaskDetails {
    val parts = name.splitCamelCase()
    var list = if (!allowNonDetekt) {
        require(parts.isNotEmpty() && parts[0] == DetektPlugin.DETEKT_TASK_NAME) {
            "Unexpected detect task name: $name"
        }
        parts.drop(1)
    } else {
        parts
    }

    val last = parts.lastOrNull()
    val isTest = last.equals("Test", ignoreCase = true)
    val isMain = !isTest && last.equals(MAIN_SOURCE_SET_POSTFIX, ignoreCase = true)
    if (isTest || isMain) {
        list = list.dropLast(1)
    }

    val isMetadata = list.firstOrNull().equals("Metadata", ignoreCase = true)
    if (isMetadata) {
        list = list.drop(1)
    }

    var platform = detectPlatformFromString(list.firstOrNull())

    // Android build tasks
    @Suppress("ComplexCondition")
    if (platform == DetectedTaskPlatform.UNKNOWN &&
        list.firstOrNull().equals("assemble", ignoreCase = true) &&
        list.lastOrNull().let {
            it.equals(RELEASE, ignoreCase = true) ||
                it.equals(DEBUG, ignoreCase = true)
        }
    ) {
        // Android assemble: $name
        platform = DetectedTaskPlatform.ANDROID
    }

    return DetectedTaskDetails(
        platform = platform,
        isMain = isMain,
        isTest = isTest,
        isMetadata = isMetadata,
        taskName = name,
    )
}

@Suppress("CyclomaticComplexMethod")
private fun detectPlatformFromString(platform: String?): DetectedTaskPlatform? = when {
    platform.isNullOrEmpty() ||
        platform.equals("Common", ignoreCase = true) ||
        platform.equals("Native", ignoreCase = true)
    -> null

    platform.equals("Js", ignoreCase = true) -> DetectedTaskPlatform.JS
    platform.equals("Wasm", ignoreCase = true) -> DetectedTaskPlatform.WASM
    platform.equals("Linux", ignoreCase = true) -> DetectedTaskPlatform.LINUX

    platform.equals("Android", ignoreCase = true) ||
        platform.equals("bundle", ignoreCase = true) // Android AAR build tasks
    -> DetectedTaskPlatform.ANDROID

    platform.equals("Mingw", ignoreCase = true) ||
        platform.equals("Win", ignoreCase = true) ||
        platform.equals("Windows", ignoreCase = true)
    -> DetectedTaskPlatform.MINGW

    platform.equals("Jvm", ignoreCase = true) ||
        platform.equals("Jmh", ignoreCase = true) ||
        platform.equals("Dokka", ignoreCase = true) ||
        platform.equals("Java", ignoreCase = true)
    -> DetectedTaskPlatform.JVM

    platform.equals("Darwin", ignoreCase = true) ||
        platform.equals("Apple", ignoreCase = true) ||
        platform.equals("Ios", ignoreCase = true) ||
        platform.equals("Watchos", ignoreCase = true) ||
        platform.equals("Tvos", ignoreCase = true) ||
        platform.equals("Macos", ignoreCase = true)
    -> DetectedTaskPlatform.APPLE

    else -> DetectedTaskPlatform.UNKNOWN
}

private data class DetectedTaskDetails(
    val platform: DetectedTaskPlatform?,
    val isMain: Boolean,
    val isTest: Boolean,
    val isMetadata: Boolean,
    val taskName: String,
)

/**
 * @see org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
 * @see org.jetbrains.kotlin.konan.target.Family
 */
private enum class DetectedTaskPlatform {
    APPLE,
    LINUX,
    MINGW,
    JS,
    WASM,
    ANDROID,
    JVM,
    UNKNOWN,

    // TODO: Detect common/metadata tasks?
}

private fun FluxoKmpConfContext.isTaskAllowed(platform: DetectedTaskPlatform?): Boolean {
    // TODO: Improve detekt task platform detection.
    //  task :detekt detected as platform UNKNOWN
    //  task :detektMetadataMain detected as platform UNKNOWN
    //  task :detektMetadataCommonJsMain detected as platform UNKNOWN
    //  task :detektMetadataCommonMain detected as platform UNKNOWN
    //  task :detektMetadataNativeMain detected as platform UNKNOWN
    //  task :detektMetadataNonJvmMain detected as platform UNKNOWN
    //  task :detektMetadataUnixMain detected as platform UNKNOWN

    return when (platform) {
        // Always allow common tasks or tasks for unknown platforms.
        null, DetectedTaskPlatform.UNKNOWN -> true
        else -> platform.toKmpTargetCodes().any(::isTargetEnabled)
    }
}

private fun DetectedTaskPlatform?.toKmpTargetCodes(): Array<KmpTargetCode> {
    return when (this) {
        DetectedTaskPlatform.APPLE -> KmpTargetCode.APPLE
        DetectedTaskPlatform.LINUX -> KmpTargetCode.LINUX
        DetectedTaskPlatform.MINGW -> KmpTargetCode.MINGW
        DetectedTaskPlatform.JS -> arrayOf(KmpTargetCode.JS)
        DetectedTaskPlatform.WASM -> KmpTargetCode.COMMON_WASM
        DetectedTaskPlatform.ANDROID -> arrayOf(KmpTargetCode.ANDROID)
        DetectedTaskPlatform.JVM -> arrayOf(KmpTargetCode.JVM)
        null, DetectedTaskPlatform.UNKNOWN -> arrayOf()
    }
}
