@file:Suppress("KDocUnresolvedReference", "NestedBlockDepth")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.BinaryCompatibilityValidatorConfig
import fluxo.conf.dsl.DEFAULT_CONSTRUCTOR_MARKER_CLASS
import fluxo.conf.dsl.JVM_SYNTHETIC_CLASS
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.withType
import fluxo.log.l
import java.nio.file.Files
import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.ExperimentalBCVApi
import kotlinx.validation.KotlinApiBuildTask
import kotlinx.validation.KotlinApiCompareTask
import kotlinx.validation.api.klib.KlibSignatureVersion
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskProvider
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

internal fun setupBinaryCompatibilityValidator(
    conf: FluxoConfigurationExtensionImpl,
    project: Project = conf.project,
): Unit = project.run r@{
    val ctx = conf.ctx
    val config = conf.apiValidationGetter
    val disabledByRelease = ctx.isRelease && config?.disableForNonRelease == true
    if (disabledByRelease || ctx.testsDisabled) {
        val isCalledExplicitly = ctx.startTaskNames.any {
            API_DUMP_SPEC.isSatisfiedBy(it) || API_CHECK_SPEC.isSatisfiedBy(it)
        }
        if (!isCalledExplicitly) {
            if (!ctx.isIncludedBuild) {
                logger.l("BinaryCompatibilityValidator checks are disabled")
            }
            return
        }
    }

    when (val type = conf.mode) {
        ConfigurationType.KOTLIN_MULTIPLATFORM ->
            setupKmpBinaryCompatibilityValidator(config, ctx)

        ConfigurationType.ANDROID_LIB,
        ConfigurationType.KOTLIN_JVM,
        ConfigurationType.GRADLE_PLUGIN,
        -> setupBinaryCompatibilityValidator(config, ctx)

        else ->
            error("Unsupported project type for BinaryCompatibilityValidator checks: $type")
    }
}

private fun Project.setupKmpBinaryCompatibilityValidator(
    config: BinaryCompatibilityValidatorConfig?,
    ctx: FluxoKmpConfContext,
) {
    setupBinaryCompatibilityValidator(config, ctx)
    setupKmpAndroidMainApiValidation(ctx)

    if (config?.tsApiChecks != false && ctx.isTargetEnabled(KmpTargetCode.JS)) {
        setupBinaryCompatibilityValidatorTs(config, ctx)
    }

    // API checks are available only for JVM and Android targets.
    if (!ctx.isTargetEnabled(KmpTargetCode.JVM) || !ctx.isTargetEnabled(KmpTargetCode.ANDROID)) {
        tasks.withType<KotlinApiCompareTask> {
            val target = getTargetForTaskName(taskName = name)
            if (target != null) {
                val isAllowed = ctx.isMultiplatformApiTargetAllowed(target)
                if (!isAllowed && enabled) {
                    enabled = false
                    logger.l("API check $this disabled!")
                }
            }
        }
    }
}

private fun Project.setupBinaryCompatibilityValidator(
    config: BinaryCompatibilityValidatorConfig?,
    ctx: FluxoKmpConfContext,
) {
    logger.l("Setup BinaryCompatibilityValidator")

    ctx.loadAndApplyPluginIfNotApplied(
        id = KOTLINX_BCV_PLUGIN_ID,
        className = KOTLINX_BCV_PLUGIN_CLASS_NAME,
        catalogPluginIds = KOTLINX_BCV_PLUGIN_ALIASES,
        catalogVersionIds = KOTLINX_BCV_PLUGIN_ALIASES,
        project = this,
        // Explicit classpath for direct plugin classes interaction.
        canLoadDynamically = false,
    ).orThrow()

    configureExtension<ApiValidationExtension>(KOTLINX_BCV_EXTENSION_NAME) {
        if (config != null) {
            ignoredPackages += config.ignoredPackages
            nonPublicMarkers += config.nonPublicMarkers
            ignoredClasses += config.ignoredClasses
            configureKlibValidation(config)
        } else {
            nonPublicMarkers.add(JVM_SYNTHETIC_CLASS)
            // Sealed classes constructors are not actually public
            ignoredClasses.add(DEFAULT_CONSTRUCTOR_MARKER_CLASS)
        }
    }
}

private fun Project.setupKmpAndroidMainApiValidation(ctx: FluxoKmpConfContext) {
    if (!ctx.isTargetEnabled(KmpTargetCode.ANDROID)) {
        return
    }

    plugins.withId(KOTLINX_BCV_PLUGIN_ID) {
        if (tasks.names.contains(ANDROID_API_CHECK_TASK)) {
            return@withId
        }
        val kotlin = extensions.findByName("kotlin") as? KotlinMultiplatformExtension
            ?: return@withId
        kotlin.targets
            .matching { it.platformType == KotlinPlatformType.androidJvm }
            .configureEach {
                // AGP-9 KMP exposes `main`/`hostTest` compilations (no `release`); wire the
                // Android-main API lane off the main compilation's classes.
                // `?.let`, not `?: return@configureEach`: an elvis-return out of this inlined
                // receiver lambda makes Kotlin's CFG mark the whole body unreachable
                // (spurious UNREACHABLE_CODE/UNUSED_VARIABLE), silently dropping the lane.
                compilations.findByName("main")?.let {
                    registerAndroidMainApiTasks(it.output.classesDirs)
                }
            }
    }
}

private fun Project.registerAndroidMainApiTasks(classesDirs: ConfigurableFileCollection) {
    val extension = extensions.getByType(ApiValidationExtension::class.java)
    val projectName = name
    val dumpFileName = "$projectName.api"
    val generatedAndroidApiFile = layout.buildDirectory.file("$API_DIR/android/$dumpFileName")
    val projectAndroidApiFile = layout.projectDirectory.file("$API_DIR/android/$dumpFileName")
    val apiEnabled = projectName !in extension.ignoredProjects && !extension.validationDisabled

    val apiBuild = tasks.register(ANDROID_API_BUILD_TASK, KotlinApiBuildTask::class.java) {
        isEnabled = apiEnabled
        description = "Builds Kotlin API for the Android main compilation of $projectName"
        inputClassesDirs.from(classesDirs)
        outputApiFile.set(generatedAndroidApiFile)
        runtimeClasspath.from(configurations.named("bcv-rt-jvm-cp-resolver"))
    }

    val apiCheck = tasks.register(ANDROID_API_CHECK_TASK, KotlinApiCompareTask::class.java) {
        isEnabled = apiEnabled
        group = "verification"
        description = "Checks Android main signatures against the golden API file for $projectName"
        projectApiFile.set(projectAndroidApiFile)
        generatedApiFile.set(apiBuild.flatMap { task -> task.outputApiFile })
    }

    val apiDump = tasks.register(ANDROID_API_DUMP_TASK) {
        enabled = apiEnabled
        group = "other"
        description = "Syncs the Android main API file for $projectName"
        dependsOn(apiBuild)
        inputs.file(apiBuild.flatMap { task -> task.outputApiFile })
        outputs.file(projectAndroidApiFile)
        doLast {
            val fromFile = generatedAndroidApiFile.get().asFile
            val toFile = projectAndroidApiFile.asFile
            if (fromFile.exists()) {
                fromFile.copyTo(toFile, overwrite = true)
            } else {
                Files.deleteIfExists(toFile.toPath())
            }
        }
    }

    tasks.named("apiCheck") { dependsOn(apiCheck) }
    tasks.named("apiDump") { dependsOn(apiDump) }
}

@OptIn(ExperimentalBCVApi::class)
private fun ApiValidationExtension.configureKlibValidation(
    config: BinaryCompatibilityValidatorConfig,
) {
    klib.enabled = config.klibValidationEnabled
    config.klibSignatureVersion?.let {
        klib.signatureVersion = KlibSignatureVersion.of(it)
    }
}

private const val KOTLINX_BCV_PLUGIN_ID: String =
    "org.jetbrains.kotlinx.binary-compatibility-validator"
private const val ANDROID_API_BUILD_TASK = "androidApiBuild"
private const val ANDROID_API_CHECK_TASK = "androidApiCheck"
private const val ANDROID_API_DUMP_TASK = "androidApiDump"

private val KOTLINX_BCV_PLUGIN_ALIASES = arrayOf(
    "bcv",
    "kotlinx-bcv",
    "kotlinx-binCompatValidator",
    "binCompatValidator",
)

private fun getTargetForTaskName(taskName: String): ApiTarget? {
    val targetName = taskName.removeSuffix("ApiCheck").takeUnless { it == taskName } ?: return null

    return when (targetName) {
        "android" -> ApiTarget.ANDROID
        "jvm" -> ApiTarget.JVM
        "js" -> ApiTarget.JS
        else -> error("Unsupported API check task name: $taskName")
    }
}

private fun FluxoKmpConfContext.isMultiplatformApiTargetAllowed(target: ApiTarget): Boolean =
    when (target) {
        ApiTarget.ANDROID -> isTargetEnabled(KmpTargetCode.ANDROID)
        ApiTarget.JVM -> isTargetEnabled(KmpTargetCode.JVM)
        ApiTarget.JS -> isTargetEnabled(KmpTargetCode.JS)
    }


private enum class ApiTarget {
    ANDROID,
    JVM,
    JS,
}


/**
 * @see kotlinx.validation.configureCheckTasks
 * @see kotlinx.validation.KotlinApiBuildTask
 */
internal fun Project.bindToApiDumpTasks(task: TaskProvider<*>, optional: Boolean = false) {
    val tasks = tasks
    plugins.withId(KOTLINX_BCV_PLUGIN_ID) {
        val apiDumpTasks = tasks.namedCompat(API_DUMP_SPEC)
        if (!optional) {
            apiDumpTasks.configureEach { this.finalizedBy(task) }
        }
        task.configure { this.dependsOn(apiDumpTasks) }

        // Fix the issue with Gradle:
        //  "Task 'apiCheck' uses this output of task 'apiDump'
        //  without declaring an explicit or implicit dependency."
        val apiCheckTasks = tasks.namedCompat(API_CHECK_SPEC)
        apiCheckTasks.configureEach {
            this.mustRunAfter(apiDumpTasks)
        }
    }
}

/** @see kotlinx.validation.BinaryCompatibilityValidatorPlugin */
private const val KOTLINX_BCV_PLUGIN_CLASS_NAME =
    "kotlinx.validation.BinaryCompatibilityValidatorPlugin"

private const val KOTLINX_BCV_EXTENSION_NAME = "apiValidation"


// apiDump
private val API_DUMP_SPEC = Spec<String> {
    it.startsWith("api") && it.endsWith("Dump")
}

// apiCheck
private val API_CHECK_SPEC = Spec<String> {
    it.startsWith("api") && it.endsWith("Check")
}


/**
 * Fallback name of the directory holding BCV's API dumps.
 *
 * BCV ≥ 0.14.0 exposes per-project customisation via
 * [kotlinx.validation.ApiValidationExtension.apiDumpDirectory], but does NOT
 * publish a top-level `API_DIR` constant — so this stays as the fallback used
 * when consumers haven't customised the directory.
 *
 * Wiring the customised value through to [fluxo.shrink.ShrinkerKeepRulesFromApiTask]
 * and [fluxo.artifact.proc.SetupArtifactsProcessing] requires capturing the
 * extension value at configuration time and routing it as a CC-safe task input
 * — a deliberate refactor scheduled with the next BCV bump, not a drop-in swap.
 */
internal const val API_DIR = "api"
