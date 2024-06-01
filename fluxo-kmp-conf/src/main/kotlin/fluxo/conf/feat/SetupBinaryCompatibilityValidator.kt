package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.FLUXO_BCV_JS_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.FLUXO_BCV_JS_PLUGIN_ID
import fluxo.conf.data.BuildConstants.FLUXO_BCV_JS_PLUGIN_VERSION
import fluxo.conf.data.BuildConstants.KOTLINX_BCV_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.KOTLINX_BCV_PLUGIN_ALIAS2
import fluxo.conf.data.BuildConstants.KOTLINX_BCV_PLUGIN_ID
import fluxo.conf.data.BuildConstants.KOTLINX_BCV_PLUGIN_VERSION
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
import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Project
import org.gradle.api.specs.Spec
import org.gradle.api.tasks.TaskProvider

internal fun setupBinaryCompatibilityValidator(
    conf: FluxoConfigurationExtensionImpl,
    project: Project = conf.project,
) = project.run r@{
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

    if (config?.jsApiChecks != false && ctx.isTargetEnabled(KmpTargetCode.JS)) {
        logger.l("Setup Fluxo TS-based BinaryCompatibilityValidator for JS")
        ctx.loadAndApplyPluginIfNotApplied(
            id = FLUXO_BCV_JS_PLUGIN_ID,
            className = FLUXO_BCV_JS_PLUGIN_CLASS_NAME,
            version = FLUXO_BCV_JS_PLUGIN_VERSION,
            catalogPluginId = FLUXO_BCV_JS_PLUGIN_ALIAS,
            project = this,
        )
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
        version = KOTLINX_BCV_PLUGIN_VERSION,
        catalogPluginIds = arrayOf(KOTLINX_BCV_PLUGIN_ALIAS, KOTLINX_BCV_PLUGIN_ALIAS2),
        project = this,
        // Explicit classpath for direct plugin classes interaction.
        canLoadDynamically = false,
    ).orThrow()

    configureExtension<ApiValidationExtension>(KOTLINX_BCV_EXTENSION_NAME) {
        if (config != null) {
            ignoredPackages += config.ignoredPackages
            nonPublicMarkers += config.nonPublicMarkers
            ignoredClasses += config.ignoredClasses
        } else {
            nonPublicMarkers.add(JVM_SYNTHETIC_CLASS)
            // Sealed classes constructors are not actually public
            ignoredClasses.add(DEFAULT_CONSTRUCTOR_MARKER_CLASS)
        }
    }
}

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
context(Project)
internal fun TaskProvider<*>.bindToApiDumpTasks(optional: Boolean = false) {
    val tasks = tasks
    val task = this
    plugins.withId(KOTLINX_BCV_PLUGIN_ID) {
        val apiDumpTasks = tasks.namedCompat(API_DUMP_SPEC)
        if (!optional) {
            apiDumpTasks.configureEach { finalizedBy(task) }
        }
        configure { dependsOn(apiDumpTasks) }

        // Fix the issue with Gradle:
        //  "Task 'apiCheck' uses this output of task 'apiDump'
        //  without declaring an explicit or implicit dependency."
        val apiCheckTasks = tasks.namedCompat(API_CHECK_SPEC)
        apiCheckTasks.configureEach {
            mustRunAfter(apiDumpTasks)
        }
    }
}


/** @see fluxo.bcvjs.FluxoBcvJsPlugin */
private const val FLUXO_BCV_JS_PLUGIN_CLASS_NAME = "fluxo.bcvjs.FluxoBcvJsPlugin"

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
 *
 * @fixme This is a copy of [kotlinx.validation.API_DIR] before 0.14.0
 *   After 0.14.0 it can be customized and requires special support.
 *
 * @see kotlinx.validation.API_DIR
 */
@Deprecated("Should be replaced with dynamic value from kotlinx.validation")
internal const val API_DIR = "api"
