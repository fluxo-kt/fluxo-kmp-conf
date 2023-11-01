@file:Suppress("CyclomaticComplexMethod")

package fluxo.conf.impl.kotlin

import MAIN_SOURCE_SET_NAME
import TEST_SOURCE_SET_NAME
import com.android.build.gradle.TestedExtension
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.impl.*
import fluxo.conf.dsl.container.impl.target.TargetAndroidContainer
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType.*
import fluxo.conf.feat.setupVerification
import fluxo.conf.impl.*
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.setupAndroidCommon
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.*
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@Suppress("LongMethod")
internal fun configureKotlinJvm(
    type: ConfigurationType,
    configuration: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
) {
    require(type != KOTLIN_MULTIPLATFORM) { "Unexpected ConfigurationType: $type" }
    if (!checkIfNeedToConfigure(type, configuration, containers)) {
        return
    }

    val project = configuration.project
    val context = configuration.context

    val isApp = type === ANDROID_APP
    if (isApp) {
        context.loadAndApplyPluginIfNotApplied(id = ANDROID_APP_PLUGIN_ID, project = project)
    } else if (type === ANDROID_LIB) {
        context.loadAndApplyPluginIfNotApplied(id = ANDROID_LIB_PLUGIN_ID, project = project)
    } else if (type === IDEA_PLUGIN) {
        context.loadAndApplyPluginIfNotApplied(id = INTELLIJ_PLUGIN_ID, project = project)

        // IDEA plugins require Java 11
        val jvmTarget = configuration.jvmTarget
        if (jvmTarget == null || jvmTarget.asJvmMajorVersion() < 11) {
            configuration.jvmTarget = "11"
        }
    }

    context.loadAndApplyPluginIfNotApplied(id = KOTLIN_JVM_PLUGIN_ID, project = project)

    if (type === ConfigurationType.GRADLE_PLUGIN) {
        // Gradle Kotlin DSL uses same compiler plugin (sam.with.receiver)
        context.loadAndApplyPluginIfNotApplied(id = KT_SAM_RECEIVER_PLUGIN_ID, project = project)
        context.loadAndApplyPluginIfNotApplied(id = GRADLE_PLUGIN_PUBLISH_ID, project = project)
    }

    // Add all plugins first, for configuring in next steps.
    val pluginManager = project.pluginManager
    for (container in containers) {
        (container as? ContainerImpl)?.applyPluginsWith(pluginManager)
    }

    project.configureExtension<KotlinProjectExtension>(name = "kotlin") {
        require(this is KotlinJvmProjectExtension || this is KotlinAndroidProjectExtension) {
            "use `setupMultiplatform` for KMP module or Kotlin/JS usage. \n" +
                    "Unexpected KotlinProjectExtension: ${javaClass.name}"
        }

        // Set Kotlin settings before the containers so that they may be overridden if desired.
        setupKotlinExtensionAndProject(configuration)
        val deps = project.dependencies
        deps.setupKotlinDependencies(context.libs, context.kotlinConfig, isApplication = isApp)

        var androidSetUp = false
        for (container in containers) {
            when (container) {
                is ContainerKotlinMultiplatformAware -> when (type) {
                    ANDROID_APP -> if (container is TargetAndroidContainer.App) {
                        androidSetUp = true
                        applyKmpContainer(container, project)
                    }

                    ANDROID_LIB -> if (container is TargetAndroidContainer.Library) {
                        androidSetUp = true
                        applyKmpContainer(container, project)
                    }

                    else -> {} // Do nothing.
                }

                is ContainerKotlinAware<*> ->
                    @Suppress("UNCHECKED_CAST")
                    (container as ContainerKotlinAware<KotlinProjectExtension>)
                        .setup(this)
            }
        }

        if (!androidSetUp) {
            project.configureExtension<TestedExtension>(name = "android") {
                setupAndroidCommon(configuration.project, configuration, configuration.context)
            }
        }
    }
}

private fun KotlinProjectExtension.applyKmpContainer(
    container: TargetAndroidContainer<*>,
    project: Project,
) {
    container.lazyTargetConf((this as KotlinAndroidProjectExtension).target)
    container.setupAndroid(project)
}


internal fun configureKotlinMultiplatform(
    configuration: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
) {
    if (!checkIfNeedToConfigure(KOTLIN_MULTIPLATFORM, configuration, containers)) {
        return
    }

    val project = configuration.project
    configuration.context.loadAndApplyPluginIfNotApplied(id = KMP_PLUGIN_ID, project = project)

    // Add all plugins first, for configuring in next steps.
    val pluginManager = project.pluginManager
    for (container in containers) {
        (container as? ContainerImpl)?.applyPluginsWith(pluginManager)
    }

    project.configureExtension<KotlinMultiplatformExtension>("kotlin") {
        // Set Kotlin settings before the containers so that they may be overridden if desired.
        setupKotlinExtensionAndProject(configuration)
        setupMultiplatformDependencies(configuration)

        for (container in containers) {
            when (container) {
                is ContainerKotlinMultiplatformAware ->
                    container.setup(this)

                is ContainerKotlinAware<*> ->
                    @Suppress("UNCHECKED_CAST")
                    (container as ContainerKotlinAware<KotlinProjectExtension>)
                        .setup(this)
            }
        }
    }
}

private fun checkIfNeedToConfigure(
    type: ConfigurationType,
    configuration: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
): Boolean {
    val logger = configuration.project.logger
    val hasAnyTarget = containers.any { it is KmpTargetContainer<*> }
    val label = ':' + when (type) {
        KOTLIN_MULTIPLATFORM -> "setupMultiplatform"
        ANDROID_LIB -> "setupAndroidLibrary"
        ANDROID_APP -> "setupAndroidApp"
        ConfigurationType.KOTLIN_JVM -> "setupKotlin"
        IDEA_PLUGIN -> "setupIdeaPlugin"
        ConfigurationType.GRADLE_PLUGIN -> "setupGradlePlugin"
    }
    if (!hasAnyTarget) {
        logger.w("$label - no applicable Kotlin targets found, skipping module configuration")
        return false
    }
    logger.i(label)
    return true
}


private fun KotlinProjectExtension.setupKotlinExtensionAndProject(
    conf: FluxoConfigurationExtensionImpl,
) {
    val project = conf.project
    project.logger.v("Configuring Kotlin extension")

    val ctx = conf.context
    val kc = conf.KotlinConfig(project, k = this)
    ctx.kotlinConfig = kc

    project.group = conf.group
    project.version = conf.version
    project.description = conf.description

    if (kc.setupKsp) ctx.loadAndApplyPluginIfNotApplied(id = KSP_PLUGIN_ID, project = project)
    if (kc.setupKapt) ctx.loadAndApplyPluginIfNotApplied(id = KAPT_PLUGIN_ID, project = project)

    setupJvmCompatibility(project, kc)

    coreLibrariesVersion = kc.coreLibs

    setupTargets(conf)
    setupSourceSetsKotlinCompatibility(kc)

    // TODO: Check KSP setup for KMP modules
    if (kc.setupKsp && this is KotlinJvmProjectExtension) {
        sourceSets[MAIN_SOURCE_SET_NAME].apply {
            kotlin.srcDir("build/generated/ksp/main/kotlin")
            resources.srcDir("build/generated/ksp/main/resources")
        }
        sourceSets[TEST_SOURCE_SET_NAME].kotlin.srcDir("build/generated/ksp/test/kotlin")
    }

    if (conf.setupVerification != false) {
        project.setupVerification(conf)
    }
}

private fun KotlinProjectExtension.setupTargets(
    conf: FluxoConfigurationExtensionImpl,
) = setupTargets {
    val compilations = compilations
    compilations.all {
        val isExperimentalTest = isExperimentalTestCompilation
        val isTest = isExperimentalTest || isTestRelated()

        setupKotlinCompatibility(
            conf = conf,
            isTest = isTest,
            isExperimentalTest = isExperimentalTest,
        )

        val context = conf.context
        val kc = context.kotlinConfig
        kotlinOptions.apply {
            val platformType = target.platformType
            val isAndroid = platformType.let { KotlinPlatformType.androidJvm === it }
            val isJs = !isAndroid && platformType
                .let { KotlinPlatformType.js === it || KotlinPlatformType.wasm === it }

            val warningsAsErrors = kc.warningsAsErrors &&
                    !isJs && !isTest && (context.isCI || context.isRelease)

            if (warningsAsErrors) {
                allWarningsAsErrors = true
            }

            val jvmTargetVersion = kc.jvmTargetVersion(
                isTest = isTest,
                latestSettings = isExperimentalTest,
            )
            jvmTargetVersion?.let(::setupJvmCompatibility)

            setupKotlinOptions(
                conf = conf,
                compilationName = name,
                warningsAsErrors = warningsAsErrors,
                latestSettings = isExperimentalTest,
                jvmTargetVersion = jvmTargetVersion,
                isAndroid = isAndroid,
            )
        }

        if (isTest && context.testsDisabled) {
            disableCompilation()
        }

        // Create experimental test compilation with the latest Kotlin settings.
        else if (kc.latestCompilation
            && name == MAIN_SOURCE_SET_NAME
            && platformType != KotlinPlatformType.common
        ) {
            compilations.createExperimentalTestCompilation(this, conf)
        }
    }

    // Verify that all unneeded targets are disabled.
    if (SHOW_DEBUG_LOGS) {
        checkForDisabledTarget(conf)
    }
}

private fun KotlinProjectExtension.setupTargets(action: Action<in KotlinTarget>) {
    when (this) {
        is KotlinSingleTargetExtension<*> -> action.execute(target)
        is KotlinMultiplatformExtension -> targets.all(action)
    }
}


private fun KotlinTarget.checkForDisabledTarget(conf: FluxoConfigurationExtensionImpl) {
    val target = this
    val project = conf.project
    val logger = project.logger
    val kmpTargetCode = KmpTargetCode.fromKotlinTarget(target, logger)
    if (kmpTargetCode == null || conf.context.isTargetEnabled(kmpTargetCode)) {
        return
    }

    logger.e("Unexpected target {}, disabling", target)
    disableCompilations()
    if (platformType != KotlinPlatformType.js) {
        return
    }

    /**
     * @see org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsSetupTask
     * @see org.jetbrains.kotlin.gradle.targets.js.npm.PublicPackageJsonTask
     * @see org.jetbrains.kotlin.gradle.targets.js.npm.tasks.KotlinNpmInstallTask
     * @see org.jetbrains.kotlin.gradle.targets.js.typescript.TypeScriptValidationTask
     * @see org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockStoreTask
     */
    project.afterEvaluate {
        tasks.matching { task ->
            task::class.java.name
                .startsWith("org.jetbrains.kotlin.gradle.targets.js.")
        }.configureEach(getDisableTaskAction(target))
    }
}