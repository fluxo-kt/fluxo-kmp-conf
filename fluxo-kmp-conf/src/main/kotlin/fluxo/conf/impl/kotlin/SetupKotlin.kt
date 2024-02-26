package fluxo.conf.impl.kotlin

import MAIN_SOURCE_SET_NAME
import TEST_SOURCE_SET_NAME
import com.android.build.gradle.TestedExtension
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.impl.ContainerImpl
import fluxo.conf.dsl.container.impl.ContainerKotlinAware
import fluxo.conf.dsl.container.impl.ContainerKotlinMultiplatformAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainer
import fluxo.conf.dsl.container.impl.target.TargetAndroidContainer
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.ConfigurationType.ANDROID_APP
import fluxo.conf.dsl.impl.ConfigurationType.ANDROID_LIB
import fluxo.conf.dsl.impl.ConfigurationType.GRADLE_PLUGIN
import fluxo.conf.dsl.impl.ConfigurationType.IDEA_PLUGIN
import fluxo.conf.dsl.impl.ConfigurationType.KOTLIN_MULTIPLATFORM
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.dsl.impl.builderMethod
import fluxo.conf.feat.setupVerification
import fluxo.conf.impl.SHOW_DEBUG_LOGS
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_EXT_NAME
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_PLUGIN_NOT_IN_CLASSPATH_ERROR
import fluxo.conf.impl.android.setupAndroidCommon
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.e
import fluxo.conf.impl.get
import fluxo.conf.impl.getDisableTaskAction
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.l
import fluxo.conf.impl.uncheckedCast
import fluxo.conf.impl.v
import fluxo.conf.impl.w
import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun configureKotlinJvm(
    conf: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
): Boolean {
    val type = conf.mode
    require(type != KOTLIN_MULTIPLATFORM) { "Unexpected Kotlin Multiplatform configuration" }
    if (!checkIfNeedToConfigure(type, conf, containers)) {
        return false
    }

    val project = conf.project
    val ctx = conf.ctx

    val isApp = type === ANDROID_APP
    if (isApp) {
        ctx.loadAndApplyPluginIfNotApplied(id = ANDROID_APP_PLUGIN_ID, project = project)
    } else if (type === ANDROID_LIB) {
        ctx.loadAndApplyPluginIfNotApplied(id = ANDROID_LIB_PLUGIN_ID, project = project)
    } else if (type === IDEA_PLUGIN) {
        ctx.loadAndApplyPluginIfNotApplied(id = INTELLIJ_PLUGIN_ID, project = project)

        // IDEA plugins require Java 11
        val jvmTarget = conf.jvmTarget
        if (jvmTarget == null || jvmTarget.asJvmMajorVersion() < JRE_11) {
            conf.jvmTarget = JRE_11.toString()
        }
    }

    ctx.loadAndApplyPluginIfNotApplied(id = KOTLIN_JVM_PLUGIN_ID, project = project)

    if (type === GRADLE_PLUGIN) {
        // Gradle Kotlin DSL uses the same compiler plugin (sam.with.receiver).
        // Allow the same ease of use for Gradle plugins.
        // Works for Gradle Action and potentially other similar types.
        project.setupSamWithReceiver(ctx)

        // Main plugin for Gradle plugins authoring and publication
        project.setupGradlePublishPlugin(ctx)
    }

    // Add all plugins first, for configuring in next steps.
    val pluginManager = project.pluginManager
    for (container in containers) {
        (container as? ContainerImpl)?.applyPluginsWith(pluginManager)
    }

    project.configureExtension<KotlinProjectExtension>(name = KOTLIN_EXT) {
        require(this is KotlinJvmProjectExtension || this is KotlinAndroidProjectExtension) {
            "use `setupMultiplatform` for KMP module or Kotlin/JS usage. \n" +
                "Unexpected KotlinProjectExtension: ${javaClass.name}"
        }

        // Set Kotlin settings before the containers so that they may be overridden if desired.
        setupKotlinExtensionAndProject(conf)

        if (conf.setupDependencies) {
            val deps = project.dependencies
            with(project) {
                deps.setupKotlinDependencies(
                    ctx.libs,
                    conf.kotlinConfig,
                    isApplication = isApp,
                )
            }
        } else {
            project.logger.v("Configuring Kotlin dependencies disabled")
        }

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
                    uncheckedCast<ContainerKotlinAware<KotlinProjectExtension>>(container)
                        .setup(this)
            }
        }

        if (!androidSetUp) {
            project.configureExtensionIfAvailable<TestedExtension>(ANDROID_EXT_NAME) {
                setupAndroidCommon(conf)
            }
        }
    }

    return true
}

private fun KotlinProjectExtension.applyKmpContainer(
    container: TargetAndroidContainer<*>,
    project: Project,
) {
    container.lazyTargetConf((this as KotlinAndroidProjectExtension).target)
    container.setupAndroid(project)
}


@Suppress("NestedBlockDepth", "CyclomaticComplexMethod")
internal fun configureKotlinMultiplatform(
    conf: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
): Boolean {
    if (!checkIfNeedToConfigure(KOTLIN_MULTIPLATFORM, conf, containers)) {
        return false
    }

    val project = conf.project
    val ctx = conf.ctx
    ctx.loadAndApplyPluginIfNotApplied(id = KOTLIN_MPP_PLUGIN_ID, project = project)

    // Add all plugins first, for configuring in next steps.
    val pluginManager = project.pluginManager
    val containerList = containers.toMutableList()
    containerList.iterator().let { iter ->
        for (container in iter) {
            val c = container as? ContainerImpl ?: continue
            try {
                c.applyPluginsWith(pluginManager)
            } catch (e: Throwable) {
                iter.remove()

                var logException = true
                var msg = e.toString()

                @Suppress("InstanceOfCheckForException")
                val isAndroidPluginUnknown = e is UnknownPluginException && "com.android." in msg
                msg = when {
                    // Special case for Android plugin.
                    isAndroidPluginUnknown -> {
                        logException = ctx.isMaxDebug
                        ANDROID_PLUGIN_NOT_IN_CLASSPATH_ERROR
                    }

                    else ->
                        "Couldn't apply ${c.name} container due to: $msg"
                }

                val ex = if (logException) e else null
                project.logger.e(msg, ex)
            }
        }
    }

    project.configureExtension<KotlinMultiplatformExtension>(KOTLIN_EXT) {
        // Set Kotlin settings before the containers so that they may be overridden if desired.
        setupKotlinExtensionAndProject(conf)

        if (conf.setupDependencies) {
            with(project) {
                setupMultiplatformDependencies(conf)
            }
        } else {
            project.logger.v("Configuring Kotlin dependencies disabled")
        }

        for (container in containerList) {
            if (SHOW_DEBUG_LOGS && container is Named) {
                project.logger.v("-> container: '${container.name}'")
            }

            when (container) {
                is ContainerKotlinMultiplatformAware ->
                    container.setup(this)

                is ContainerKotlinAware<*> ->
                    uncheckedCast<ContainerKotlinAware<KotlinProjectExtension>>(container)
                        .setup(this)
            }
        }
    }

    return true
}

private fun checkIfNeedToConfigure(
    type: ConfigurationType,
    conf: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
): Boolean {
    // TODO: Detect if KMP is already applied and what targets are already configured.

    val logger = conf.project.logger
    val hasAnyTarget = containers.any { it is KmpTargetContainer<*> }
    val label = ':' + type.builderMethod
    if (!hasAnyTarget) {
        logger.w("$label - no applicable Kotlin targets found, skipping module configuration")
        return false
    }
    logger.l(label)
    return true
}


private fun KotlinProjectExtension.setupKotlinExtensionAndProject(
    conf: FluxoConfigurationExtensionImpl,
) {
    val project = conf.project
    project.logger.v("Configuring Kotlin extension")

    val ctx = conf.ctx
    val kc = conf.KotlinConfig(project, k = this)
    conf.kotlinConfig = kc

    if (!conf.setupKotlin) {
        project.logger.l("Finishing Kotlin extension configuration early (disabled)")
    }

    project.group = conf.group
    project.version = conf.version
    project.description = conf.description

    conf.explicitApi?.let { explicitApi = it }

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

    if (conf.setupVerification != false && !ctx.testsDisabled) {
        project.setupVerification(conf)
    }
}

// FIXME: Configure common compilerOptions via KotlinProjectExtension.compilerOptions
@Suppress("CyclomaticComplexMethod")
private fun KotlinProjectExtension.setupTargets(
    conf: FluxoConfigurationExtensionImpl,
    isMultiplatform: Boolean = this is KotlinMultiplatformExtension,
) = setupTargets {
    val compilations = compilations
    compilations.configureEach compilation@{
        val isExperimentalTest = isExperimentalLatestCompilation
        val isTest = isExperimentalTest || isTestRelated()

        setupKotlinCompatibility(
            conf = conf,
            isTest = isTest,
            isExperimentalTest = isExperimentalTest,
        )

        val ctx = conf.ctx
        val kc = conf.kotlinConfig
        kotlinOptions.run {
            val platformType = target.platformType
            val isAndroid = platformType.let { KotlinPlatformType.androidJvm === it }
            val isJs = !isAndroid && platformType
                .let { KotlinPlatformType.js === it || KotlinPlatformType.wasm === it }

            val warningsAsErrors = kc.warningsAsErrors &&
                !isJs && !isTest && (ctx.isCI || ctx.isRelease)

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
                isTest = isTest,
                isMultiplatform = isMultiplatform,
            )
        }

        // Disable test compilation if tests are disabled.
        if (isTest && ctx.testsDisabled) {
            disableCompilation()
        }
    }

    // Create experimental test compilation with the latest Kotlin settings.
    // Configured automatically with `configureEach`.
    // NOTE: can't be created inside `compilations.configureEach`,
    //  it will fail due to the wrong phase.
    setupExperimentalLatestCompilation(conf, isMultiplatform = isMultiplatform)

    // Verify that all unneeded targets are disabled.
    if (SHOW_DEBUG_LOGS) {
        checkForDisabledTarget(conf)
    }
}

private fun KotlinProjectExtension.setupTargets(action: Action<in KotlinTarget>) {
    when (this) {
        is KotlinSingleTargetExtension<*> -> action.execute(target)
        is KotlinMultiplatformExtension -> targets.configureEach(action)
    }
}


private fun KotlinTarget.checkForDisabledTarget(conf: FluxoConfigurationExtensionImpl) {
    val target = this
    val project = conf.project
    val logger = project.logger
    val kmpTargetCode = KmpTargetCode.fromKotlinTarget(target, logger)
    if (kmpTargetCode == null || conf.ctx.isTargetEnabled(kmpTargetCode)) {
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


internal const val KOTLIN_EXT = "kotlin"
