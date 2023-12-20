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
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.l
import fluxo.conf.impl.withType
import kotlinx.validation.ApiValidationExtension
import kotlinx.validation.KotlinApiCompareTask
import org.gradle.api.Project

internal fun setupBinaryCompatibilityValidator(
    config: BinaryCompatibilityValidatorConfig?,
    configuration: FluxoConfigurationExtensionImpl,
    type: ConfigurationType,
    project: Project = configuration.project,
) = project.run r@{
    val context = configuration.context
    val disabledByRelease = context.isRelease && config?.disableForNonRelease == true
    if (disabledByRelease || context.testsDisabled) {
        val calledExplicitly = context.startTaskNames.any {
            it.endsWith(CHECK_TASK, ignoreCase = false) ||
                it.startsWith(DUMP_TASK, ignoreCase = false)
        }
        if (!calledExplicitly) {
            logger.l("BinaryCompatibilityValidator checks are disabled")
            return
        }
    }

    when (type) {
        ConfigurationType.KOTLIN_MULTIPLATFORM ->
            setupKmpBinaryCompatibilityValidator(config, context)

        ConfigurationType.ANDROID_LIB,
        ConfigurationType.KOTLIN_JVM,
        ConfigurationType.GRADLE_PLUGIN,
        -> setupBinaryCompatibilityValidator(config, context)

        else ->
            error("Unsupported project type for BinaryCompatibilityValidator checks: $type")
    }
}

private fun Project.setupKmpBinaryCompatibilityValidator(
    config: BinaryCompatibilityValidatorConfig?,
    context: FluxoKmpConfContext,
) {
    setupBinaryCompatibilityValidator(config, context)

    if (config?.jsApiChecks != false && context.isTargetEnabled(KmpTargetCode.JS)) {
        logger.l("Setup Fluxo TS-based BinaryCompatibilityValidator for JS")
        context.loadAndApplyPluginIfNotApplied(
            id = FLUXO_BCV_JS_PLUGIN_ID,
            className = FLUXO_BCV_JS_PLUGIN_CLASS_NAME,
            version = FLUXO_BCV_JS_PLUGIN_VERSION,
            catalogPluginId = FLUXO_BCV_JS_PLUGIN_ALIAS,
            project = this,
        )
    }

    tasks.withType<KotlinApiCompareTask> {
        val target = getTargetForTaskName(taskName = name)
        if (target != null) {
            enabled = context.isMultiplatformApiTargetAllowed(target)
            if (!enabled) {
                logger.l("API check $this disabled!")
            }
        }
    }
}

private fun Project.setupBinaryCompatibilityValidator(
    config: BinaryCompatibilityValidatorConfig?,
    context: FluxoKmpConfContext,
) {
    logger.l("Setup BinaryCompatibilityValidator")

    context.loadAndApplyPluginIfNotApplied(
        id = KOTLINX_BCV_PLUGIN_ID,
        className = KOTLINX_BCV_PLUGIN_CLASS_NAME,
        version = KOTLINX_BCV_PLUGIN_VERSION,
        catalogPluginIds = arrayOf(KOTLINX_BCV_PLUGIN_ALIAS, KOTLINX_BCV_PLUGIN_ALIAS2),
        project = this,
        canLoadDynamically = false,
    ).orThrow()

    config ?: return
    configureExtension<ApiValidationExtension>(KOTLINX_BCV_EXTENSION_NAME) {
        ignoredPackages += config.ignoredPackages
        nonPublicMarkers += config.nonPublicMarkers
        ignoredClasses += config.ignoredClasses
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

/** @see fluxo.bcvjs.FluxoBcvJsPlugin */
private const val FLUXO_BCV_JS_PLUGIN_CLASS_NAME = "fluxo.bcvjs.FluxoBcvJsPlugin"

/** @see kotlinx.validation.BinaryCompatibilityValidatorPlugin */
private const val KOTLINX_BCV_PLUGIN_CLASS_NAME =
    "kotlinx.validation.BinaryCompatibilityValidatorPlugin"

private const val KOTLINX_BCV_EXTENSION_NAME = "apiValidation"

private const val CHECK_TASK = "apiCheck"
private const val DUMP_TASK = "apiDump"
