package fluxo.conf.feat

import MAIN_SOURCE_SET_POSTFIX
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.MergeDetektBaselinesTask
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.DEBUG
import fluxo.conf.impl.android.RELEASE
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.dependencies
import fluxo.conf.impl.disableTask
import fluxo.conf.impl.e
import fluxo.conf.impl.onLibrary
import fluxo.conf.impl.register
import fluxo.conf.impl.splitCamelCase
import fluxo.conf.impl.withType
import io.github.detekt.gradle.DetektKotlinCompilerPlugin
import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.DetektCreateBaselineTask
import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import io.gitlab.arturbosch.detekt.report.ReportMergeTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.tasks.JvmConstants.TEST_TASK_NAME
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.TaskProvider
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

private const val MERGE_DETEKT_TASK_NAME = "mergeDetektSarif"

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


@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun Project.setupDetekt(
    conf: FluxoConfigurationExtensionImpl,
    ignoredBuildTypes: List<String>,
    ignoredFlavors: List<String>,
) {
    val context = conf.context
    val testsDisabled = context.testsDisabled
    if (!testsDisabled) {
        // Detekt is always availabe in the classpath as it's a dependency.
        pluginManager.apply(
            when (conf.enableDetektCompilerPlugin) {
                true -> DetektKotlinCompilerPlugin::class.java
                else -> DetektPlugin::class.java
            },
        )
    }

    val detektBaselineFile = file(DETEKT_BASELINE_FILE_NAME)
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

    val detektBaselineIntermediate = "$buildDir/intermediates/detekt/baseline"
    configureExtension<DetektExtension>(DetektPlugin.DETEKT_EXTENSION) {
        parallel = true
        buildUponDefaultConfig = true
        ignoreFailures = true
        autoCorrect = !context.isCI && !testStarted && !detektMergeStarted
        basePath = rootProject.projectDir.absolutePath

        this.ignoredBuildTypes = ignoredBuildTypes
        this.ignoredFlavors = ignoredFlavors

        val files = arrayOf(
            file("detekt.yml"),
            rootProject.file("detekt.yml"),
            rootProject.file("detekt-compose.yml"),
            rootProject.file("detekt-formatting.yml"),
        ).filter { it.exists() && it.canRead() }

        if (files.isNotEmpty()) {
            @Suppress("SpreadOperator")
            config.from(*files.toTypedArray())
        }

        baseline = if (detektMergeStarted) {
            file("$detektBaselineIntermediate.xml")
        } else {
            detektBaselineFile
        }

        if (testsDisabled) enableCompilerPlugin.set(false)
    }

    if (mergeDetektBaselinesTask != null) {
        val baselineTasks = tasks.withType<DetektCreateBaselineTask> {
            baseline.set(file("$detektBaselineIntermediate-$name.xml"))
        }
        mergeDetektBaselinesTask.configure {
            dependsOn(baselineTasks)
            baselineFiles.from(baselineTasks.map { it.baseline })
        }
    }

    val detektTasks = tasks.withType<Detekt> {
        val testsAreDisabled = context.testsDisabled
        val isDisabled = testsAreDisabled || !isDetektTaskAllowed(context)
        if (isDisabled && enabled) {
            val reason = when {
                testsAreDisabled -> "tests are disabled"
                else -> "platform ${taskPlatform()} is disabled"
            }
            logger.e("Unexpected Detekt task {}, disabling as $reason", path)
            disableTask()
        }
        context.kotlinConfig.jvmTarget?.let { jvmTarget = it }
        reports.apply {
            sarif.required.set(!isDisabled)
            html.required.set(!isDisabled)
            txt.required.set(false)
            xml.required.set(false)
        }
    }

    if (!testsDisabled) {
        val detektAll = tasks.register<Task>("detektAll") {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Calls all available Detekt tasks for this project"
            dependsOn(detektTasks)
        }
        tasks.matching { it.name == CHECK_TASK_NAME }
            .configureEach { dependsOn(detektAll) }

        context.mergeDetektTask?.configure {
            dependsOn(detektTasks)
            input.from(detektTasks.map { it.sarifReportFile })
        }

        context.libs?.run {
            dependencies {
                onLibrary("detekt-formatting", ::detektPlugins)
                onLibrary("detekt-compose", ::detektPlugins)
            }
        }
    }
}


private fun DependencyHandler.detektPlugins(dependencyNotation: Any) =
    add("detektPlugins", dependencyNotation)

private const val DETEKT_BASELINE_FILE_NAME = "detekt-baseline.xml"

private val TEST_TASK_PREFIXES = arrayOf(CHECK_TASK_NAME, TEST_TASK_NAME)


private fun Detekt.taskPlatform(): DetectedTaskPlatform {
    return getTaskDetailsFromName(name).platform
        ?: DetectedTaskPlatform.UNKNOWN
}

private fun Detekt.isDetektTaskAllowed(context: FluxoKmpConfContext): Boolean = with(context) {
    getTaskDetailsFromName(name).platform.isTaskAllowed()
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
}

context(FluxoKmpConfContext)
private fun DetectedTaskPlatform?.isTaskAllowed(): Boolean =
    toKmpTargetCodes().any(::isTargetEnabled)

private fun DetectedTaskPlatform?.toKmpTargetCodes(): Array<KmpTargetCode> {
    return when (this) {
        DetectedTaskPlatform.APPLE -> KmpTargetCode.APPLE
        DetectedTaskPlatform.LINUX -> KmpTargetCode.LINUX
        DetectedTaskPlatform.MINGW -> KmpTargetCode.MINGW
        DetectedTaskPlatform.JS -> arrayOf(KmpTargetCode.JS)
        DetectedTaskPlatform.WASM -> arrayOf(KmpTargetCode.WASM, KmpTargetCode.WASM32)
        DetectedTaskPlatform.ANDROID -> arrayOf(KmpTargetCode.ANDROID)
        DetectedTaskPlatform.JVM -> arrayOf(KmpTargetCode.JVM)
        else -> arrayOf()
    }
}
