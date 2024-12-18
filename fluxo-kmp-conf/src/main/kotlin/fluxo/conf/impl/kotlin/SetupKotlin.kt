package fluxo.conf.impl.kotlin

import MAIN_SOURCE_SET_NAME
import TEST_SOURCE_SET_NAME
import com.android.build.gradle.TestedExtension
import fkcSetupMultiplatform
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
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_EXT_NAME
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_PLUGIN_NOT_IN_CLASSPATH_ERROR
import fluxo.conf.impl.android.setupAndroidCommon
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.get
import fluxo.conf.impl.getDisableTaskAction
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.uncheckedCast
import fluxo.conf.pub.setupGradlePublishPlugin
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.e
import fluxo.log.l
import fluxo.log.v
import fluxo.log.w
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
    val hasAnyTarget = containers.any { it is KmpTargetContainer<*> }
    if (!checkIfNeedToConfigure(type, conf, hasAnyTarget)) {
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

        // IDEA plugins require Java 17
        val jvmTarget = conf.jvmTarget
        if (jvmTarget == null || jvmTarget.asJvmMajorVersion() < JRE_17) {
            conf.jvmTarget = JRE_17.toString()
        }
    }

    ctx.loadAndApplyPluginIfNotApplied(id = KOTLIN_JVM_PLUGIN_ID, project = project)

    if (type === GRADLE_PLUGIN) {
        // Gradle Kotlin DSL uses the same compiler plugin (sam.with.receiver).
        // Allow the same ease of use for Gradle plugins.
        // Works for Gradle Action and potentially other similar types.
        project.setupSamWithReceiver(ctx)

        // Main plugin for Gradle plugins authoring and publication
        project.setupGradlePublishPlugin(conf)
    }

    // Add all plugins first, for configuring in next steps.
    val pluginManager = project.pluginManager
    for (container in containers) {
        (container as? ContainerImpl)?.applyPluginsWith(pluginManager)
    }

    project.configureExtension<KotlinProjectExtension>(name = KOTLIN_EXT) {
        require(this is KotlinJvmProjectExtension || this is KotlinAndroidProjectExtension) {
            "use `${Project::fkcSetupMultiplatform.name}` for KMP module or Kotlin/JS usage. \n" +
                "Unexpected KotlinProjectExtension: ${javaClass.name}"
        }

        // Set Kotlin settings before the containers so that they may be overridden if desired.
        setupKotlinExtensionAndProject(conf)

        if (conf.setupDependencies) {
            project.setupKotlinDependencies(ctx.libs, conf.kotlinConfig, isApplication = isApp)
        } else {
            project.logger.v("NOT Configuring Kotlin dependencies (setupDependencies=false)")
        }

        var androidWasSetUp = false
        for (container in containers) {
            when (container) {
                is ContainerKotlinMultiplatformAware -> when (type) {
                    ANDROID_APP -> if (container is TargetAndroidContainer.App) {
                        androidWasSetUp = true
                        applyKmpContainer(container, project)
                    }

                    ANDROID_LIB -> if (container is TargetAndroidContainer.Library) {
                        androidWasSetUp = true
                        applyKmpContainer(container, project)
                    }

                    else -> {} // Do nothing.
                }

                is ContainerKotlinAware<*> ->
                    uncheckedCast<ContainerKotlinAware<KotlinProjectExtension>>(container)
                        .setup(this)
            }
        }

        if (!androidWasSetUp) {
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


@Suppress("NestedBlockDepth", "CyclomaticComplexMethod", "LongMethod")
internal fun configureKotlinMultiplatform(
    conf: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
): Boolean {
    val hasAnyTarget = containers.any { it is KmpTargetContainer<*> }
    if (!checkIfNeedToConfigure(KOTLIN_MULTIPLATFORM, conf, hasAnyTarget)) {
        return false
    }

    val project = conf.project
    val ctx = conf.ctx
    ctx.loadAndApplyPluginIfNotApplied(id = KOTLIN_MPP_PLUGIN_ID, project = project)

    // Add all plugins first, for configuring in next steps.
    // Remove containers that failed to apply plugins.
    val pluginManager = project.pluginManager
    val containerList = containers.toMutableList()
    containerList.iterator().let { iter ->
        for (container in containerList.iterator()) {
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
            project.setupMultiplatformDependencies(kmpe = this, conf)
        } else {
            project.logger.v("Configuring Kotlin dependencies disabled")
        }

        var androidWasSetUp = false
        for (container in containerList) {
            if (SHOW_DEBUG_LOGS && container is Named) {
                project.logger.v("-> container: '${container.name}'")
            }

            when (container) {
                is ContainerKotlinMultiplatformAware -> {
                    if (container is TargetAndroidContainer<*>) {
                        androidWasSetUp = true
                    }
                    container.setup(this)
                }

                is ContainerKotlinAware<*> ->
                    uncheckedCast<ContainerKotlinAware<KotlinProjectExtension>>(container)
                        .setup(this)
            }
        }
        if (!androidWasSetUp) {
            project.configureExtensionIfAvailable<TestedExtension>(ANDROID_EXT_NAME) {
                setupAndroidCommon(conf)
            }
        }
    }

    return true
}

private fun checkIfNeedToConfigure(
    type: ConfigurationType,
    conf: FluxoConfigurationExtensionImpl,
    hasAnyTarget: Boolean,
): Boolean {
    val logger = conf.project.logger
    val label = ':' + type.builderMethod
    if (!hasAnyTarget && conf.setupLegacyKotlinHierarchy) {
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
    project.group = conf.group
    project.version = conf.version
    project.description = conf.description

    if (!conf.setupKotlin) {
        project.logger.w("NOT Configuring Kotlin extension (setupKotlin=false)")
        return
    }
    project.logger.v("Configuring Kotlin extension")

    val ctx = conf.ctx
    val kc = conf.KotlinConfig(project, k = this)
    conf.kotlinConfig = kc

    if (kc.setupKsp) ctx.loadAndApplyPluginIfNotApplied(id = KSP_PLUGIN_ID, project = project)
    if (kc.setupKapt) ctx.loadAndApplyPluginIfNotApplied(id = KAPT_PLUGIN_ID, project = project)

    if (conf.setupJvmCompatibility) {
        setupJvmCompatibility(project, kc)
    }
    if (conf.setupKotlinOptions) {
        conf.explicitApi?.let { explicitApi = it }
        coreLibrariesVersion = kc.coreLibs

        val composeConfig = conf.setupCompose()
        setupTargets(conf, composeConfig)
        setupSourceSetsKotlinCompatibility(kc)

        // TODO: Check KSP setup for KMP modules
        if (kc.setupKsp && this is KotlinJvmProjectExtension) {
            sourceSets[MAIN_SOURCE_SET_NAME].apply {
                kotlin.srcDir("build/generated/ksp/main/kotlin")
                resources.srcDir("build/generated/ksp/main/resources")
            }
            sourceSets[TEST_SOURCE_SET_NAME].kotlin.srcDir("build/generated/ksp/test/kotlin")
        }
    }

    project.setupVerification(conf)
}

// FIXME: Configure common compilerOptions via KotlinProjectExtension.compilerOptions
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun KotlinProjectExtension.setupTargets(
    conf: FluxoConfigurationExtensionImpl,
    composeConfig: ComposeConfiguration?,
    isMultiplatform: Boolean = this is KotlinMultiplatformExtension,
) = setupTargets {
    compilations.configureEach compilation@{
        val isExperimentalTest = isExperimentalLatestCompilation
        val isTest = isExperimentalTest || isTestRelated()

        val kc = conf.kotlinConfig
        val jvmTargetVersion = kc.jvmTargetVersion(
            isTest = isTest,
            latestSettings = isExperimentalTest,
        )
        jvmTargetVersion?.let(::setupJvmCompatibility)

        // In Kotlin 1.9 `compilerOptions` are still unreliable.
        // Use `kotlinOptions` instead for now.
        val ctx = conf.ctx
        kotlinOptions.run {
            val platformType = target.platformType
            val isAndroid = platformType.let { KotlinPlatformType.androidJvm === it }
            val isJsOrWasm = !isAndroid && platformType
                .let { KotlinPlatformType.js === it || KotlinPlatformType.wasm === it }

            val warningsAsErrors = conf.kotlinConfig.warningsAsErrors &&
                !isJsOrWasm && !isTest && (ctx.isCI || ctx.isRelease)

            setupKotlinCompatibility(
                conf = conf,
                isTest = isTest,
                isExperimentalTest = isExperimentalTest,
            )

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

        if (composeConfig != null) {
            setupComposeLegacyWay(conf, composeConfig, isTest)
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
