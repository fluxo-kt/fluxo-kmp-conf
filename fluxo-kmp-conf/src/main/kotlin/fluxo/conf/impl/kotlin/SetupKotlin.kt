package fluxo.conf.impl.kotlin

import com.android.build.api.dsl.CommonExtension
import fkcSetupMultiplatform
import fluxo.conf.data.BuildConstants.KSP_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.container.impl.ContainerImpl
import fluxo.conf.dsl.container.impl.ContainerKotlinAware
import fluxo.conf.dsl.container.impl.ContainerKotlinMultiplatformAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.impl.target.TargetAndroidContainer
import fluxo.conf.dsl.impl.ConfigurationType.ANDROID_APP
import fluxo.conf.dsl.impl.ConfigurationType.ANDROID_LIB
import fluxo.conf.dsl.impl.ConfigurationType.GRADLE_PLUGIN
import fluxo.conf.dsl.impl.ConfigurationType.IDEA_PLUGIN
import fluxo.conf.dsl.impl.ConfigurationType.KOTLIN_MULTIPLATFORM
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.dsl.impl.builderMethod
import fluxo.conf.feat.disableDetektTasks
import fluxo.conf.feat.setupVerification
import fluxo.conf.impl.MAIN_SOURCE_SET_NAME
import fluxo.conf.impl.MAIN_SOURCE_SET_POSTFIX
import fluxo.conf.impl.TEST_SOURCE_SET_NAME
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_EXT_NAME
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_PLUGIN_NOT_IN_CLASSPATH_ERROR
import fluxo.conf.impl.android.AgpVersion
import fluxo.conf.impl.android.setupAndroidCommon
import fluxo.conf.impl.android.setupKmpAndroidExtension
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.get
import fluxo.conf.impl.getDisableTaskAction
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.uncheckedCast
import fluxo.conf.pub.setupGradlePublishPlugin
import fluxo.log.SHOW_DEBUG_LOGS
import fluxo.log.d
import fluxo.log.e
import fluxo.log.l
import fluxo.log.v
import fluxo.log.w
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.plugins.UnknownPluginException
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun configureKotlinJvm(
    conf: FluxoConfigurationExtensionImpl,
    containers: Array<Container>,
): Boolean {
    val type = conf.mode
    require(type != KOTLIN_MULTIPLATFORM) { "Unexpected Kotlin Multiplatform configuration" }
    val project = conf.project
    project.logger.l(':' + type.builderMethod)
    val ctx = conf.ctx

    val isApp = type === ANDROID_APP
    val isAndroid = isApp || type === ANDROID_LIB
    if (isApp) {
        ctx.loadAndApplyPluginIfNotApplied(id = ANDROID_APP_PLUGIN_ID, project = project)
    } else if (type === ANDROID_LIB) {
        ctx.loadAndApplyPluginIfNotApplied(id = ANDROID_LIB_PLUGIN_ID, project = project)
    } else if (type === IDEA_PLUGIN) {
        // No JVM target override: an IDEA plugin is a library the IDE loads, so an unset target
        // gets the library default (17, loadable on every platform IPGP 2 supports) and an
        // explicit one is kept. IPGP's own verify task warns when a platform needs newer Java.
        ctx.loadAndApplyPluginIfNotApplied(id = INTELLIJ_PLUGIN_ID, project = project)
    }

    // For Android types the `kotlin` extension is already registered by the time we get here:
    // under AGP 9 by AGP's built-in Kotlin support (`android.builtInKotlin` defaults to true),
    // under AGP 8 by the consumer-applied `org.jetbrains.kotlin.android` plugin (which AGP 9
    // hard-rejects). Applying `kotlin-jvm` on top of either would conflict with the existing
    // `kotlin` extension (`Cannot add extension with name 'kotlin'`); the extension is also of
    // a different type (`KotlinAndroidProjectExtension`) which the assertion on line 103
    // correctly accepts. Plain JVM / Gradle-plugin / IDEA-plugin paths still need the apply.
    if (!isAndroid) {
        ctx.loadAndApplyPluginIfNotApplied(id = KOTLIN_JVM_PLUGIN_ID, project = project)
    }

    if (type === GRADLE_PLUGIN) {
        // Gradle Kotlin DSL uses the same compiler plugin (sam.with.receiver).
        // Allow the same ease of use for Gradle plugins.
        // Works for Gradle Action and potentially other similar types.
        project.setupSamWithReceiver(ctx)

        // Main plugin for Gradle plugins authoring and publication
        project.setupGradlePublishPlugin(conf)

        conf.deriveGradlePluginKotlinVersion()
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
            project.configureExtensionIfAvailable<CommonExtension>(ANDROID_EXT_NAME) {
                setupAndroidCommon(conf)
            }
        }
    }

    return true
}

private fun KotlinMultiplatformExtension.connectAndroidSourceSetsToCommonJvm() {
    val sourceSets = sourceSets
    val commonJvm = KmpTargetContainerImpl.CommonJvm.COMMON_JVM

    // `commonJvmMain` is an optional intermediate: it is present only when the hierarchy
    // template materialised it (a JVM-family sibling exists) or dependency wiring registered
    // it (`setupDependencies`). An android-only KMP consumer (e.g. KMP_TARGETS=ANDROID) with
    // wiring off has neither, so there is nothing to bridge — androidMain stays linked to
    // commonMain by KGP's default hierarchy. `getByName` would throw there; `findByName` keeps
    // the bridge a no-op exactly where the intermediate is absent.
    val commonJvmMain = sourceSets.findByName(commonJvm + MAIN_SOURCE_SET_POSTFIX) ?: return
    sourceSets.findByName("androidMain")?.dependsOn(commonJvmMain)
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
    val project = conf.project
    project.logger.l(':' + KOTLIN_MULTIPLATFORM.builderMethod)
    if (containers.none { it is KmpTargetContainer<*> }) {
        val message =
            ":${KOTLIN_MULTIPLATFORM.builderMethod}" +
                " - no applicable Kotlin targets found, skipping module configuration"
        if (conf.ctx.allTargetsEnabled) {
            project.logger.w(message)
        } else {
            project.logger.v(message)
        }
        return false
    }

    val ctx = conf.ctx
    ctx.loadAndApplyPluginIfNotApplied(id = KOTLIN_MPP_PLUGIN_ID, project = project)

    // Add all plugins first, for configuring in next steps.
    // Remove containers that failed to apply plugins.
    val pluginManager = project.pluginManager
    val containerList = containers.filter { container ->
        val c = container as? ContainerImpl ?: return@filter true
        try {
            c.applyPluginsWith(pluginManager)
            true
        } catch (e: Throwable) {
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
            false
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
            project.configureExtensionIfAvailable<CommonExtension>(ANDROID_EXT_NAME) {
                setupAndroidCommon(conf)
            }
        } else {
            connectAndroidSourceSetsToCommonJvm()
        }
    }

    return true
}

/**
 * AGP 9's built-in Kotlin (an Android module without `org.jetbrains.kotlin.android`) fails
 * configuration on `org.jetbrains.kotlin.kapt`; its kapt is `com.android.legacy-kapt`. That id
 * ships in `com.android.tools.build:gradle-kotlin`, which AGP doesn't depend on, so it can be
 * applied only when the consumer put it on the build classpath. Failing then is deliberate: kapt
 * that silently doesn't run leaves annotation processors' code missing from the build.
 */
private fun KotlinProjectExtension.applyKapt(conf: FluxoConfigurationExtensionImpl) {
    val project = conf.project
    val builtInKotlin = this is KotlinAndroidProjectExtension &&
        !project.pluginManager.hasPlugin(KOTLIN_ANDROID_PLUGIN_ID)
    if (!builtInKotlin) {
        conf.ctx.loadAndApplyPluginIfNotApplied(id = KAPT_PLUGIN_ID, project = project)
        return
    }
    try {
        project.pluginManager.apply(ANDROID_KAPT_PLUGIN_ID)
    } catch (e: UnknownPluginException) {
        throw GradleException(
            "kapt in $project needs '$ANDROID_KAPT_PLUGIN_ID' with AGP 9's built-in " +
                "Kotlin, and it is not on the build classpath. Add " +
                "`id(\"$ANDROID_KAPT_PLUGIN_ID\") version \"<your AGP version>\" apply false` " +
                "to the root `plugins {}` (AGP ${AgpVersion.current(project)} here), " +
                "or move the annotation processors to KSP.",
            e,
        )
    }
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

    if (kc.setupKsp) {
        ctx.loadAndApplyPluginIfNotApplied(
            id = KSP_PLUGIN_ID,
            version = KSP_PLUGIN_VERSION,
            project = project,
            onBuildClasspath = true,
        )
        project.checkKspFitsKotlin(ctx.kotlinPluginVersion)
    }
    if (kc.setupKapt) applyKapt(conf)

    if (conf.setupJvmCompatibility) {
        setupJvmCompatibility(project, kc)
    }
    if (conf.setupKotlinOptions) {
        conf.explicitApi?.let { explicitApi = it }
        coreLibrariesVersion = kc.coreLibs

        conf.setupCompose()
        setupModuleKotlinOptions(conf)
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
    }

    project.setupVerification(conf)

    // AGP-9 KMP+Android (`com.android.kotlin.multiplatform.library`) registers a separate
    // extension type that is not a `CommonExtension`, so `setupAndroidCommon` never sees it.
    // Apply Fluxo defaults — namespace, compileSdk, minSdk — to the new extension when the
    // consumer applies the new plugin.
    project.setupKmpAndroidExtension(conf)
}

// Shared defaults sit on the module (`setupModuleKotlinOptions`) and, in KMP, on each target;
// what differs per compilation is set on its compile task here.
@Suppress("CyclomaticComplexMethod", "LongMethod")
private fun KotlinProjectExtension.setupTargets(
    conf: FluxoConfigurationExtensionImpl,
    isMultiplatform: Boolean = this is KotlinMultiplatformExtension,
) = setupTargets {
    // A single-target module's target inherits the module's options, set already; a convention
    // on the target would cut that link.
    if (isMultiplatform) setupTargetKotlinOptions(conf)
    val inheritedArgs = (this as? HasConfigurableKotlinCompilerOptions<*>)
        ?.compilerOptions?.freeCompilerArgs
    compilations.configureEach compilation@{
        val isExperimentalTest = isExperimentalLatestCompilation
        val isTest = isExperimentalTest || isTestRelated()

        val kc = conf.kotlinConfig
        val ctx = conf.ctx
        val platformType = target.platformType
        val isAndroid = platformType.let { KotlinPlatformType.androidJvm === it }
        val jvmTargetVersion = kc.jvmTargetVersion(
            isTest = isTest,
            isAndroid = isAndroid,
            latestSettings = isExperimentalTest,
        )
        jvmTargetVersion?.let { setupJvmCompatibility(it) }
        if (platformType === KotlinPlatformType.jvm && !kc.jvmTargetExplicit) {
            jvmTargetVersion?.let { v ->
                conf.hintDefaultedJvmTarget(compileTaskProvider.name, v)
                javaCompileTaskProviderCompat?.let { conf.hintDefaultedJvmTarget(it.name, v) }
            }
        }
        // Android: AGP rejects javac --release (it sets up its own boot classpath).
        if (!isAndroid && kc.useJdkRelease) {
            jvmTargetVersion?.let { v ->
                javaCompileTaskProviderCompat?.configure { limitJavaJdkApi(conf, v) }
            }
        }
        val isJsOrWasm = !isAndroid && platformType
            .let { KotlinPlatformType.js === it || KotlinPlatformType.wasm === it }

        // Shared-metadata (klib) compilations are the `common` platform target. They resolve the
        // *transformed* hierarchical metadata of every dependency, and for a diamond source-set
        // graph JetBrains' own `kotlin-stdlib` / `atomicfu` slices collide on `unique_name`,
        // emitting `w: KLIB resolver: The same 'unique_name=…' found in more than one library`
        // (KT-69310). It is an unsuppressable, non-actionable upstream false-positive — a nameless
        // resolver message (so `-Xsuppress-warning` can't target it) with no silent strategy
        // (`-Xklib-duplicated-unique-name-strategy` only offers `*-with-warning`).
        // Excluding it from `-Werror` loses no real coverage: every leaf platform compilation
        // recompiles the same common sources under `-Werror`, so genuine common-code warnings
        // still fail the build.
        val isMetadata = !isAndroid && KotlinPlatformType.common === platformType

        // The module default is on for CI and release builds (`setupModuleKotlinOptions`).
        val warningsAsErrorsOff = kc.warningsAsErrors && (ctx.isCI || ctx.isRelease) &&
            (isJsOrWasm || isMetadata || isTest)

        val compName = name
        compileTaskProvider.configure {
            compilerOptions {
                setupKotlinCompatibility(
                    conf = conf,
                    isTest = isTest,
                    isExperimentalTest = isExperimentalTest,
                )

                setupKotlinOptions(
                    conf = conf,
                    compilationName = compName,
                    warningsAsErrorsOff = warningsAsErrorsOff,
                    latestSettings = isExperimentalTest,
                    jvmTargetVersion = jvmTargetVersion,
                    isAndroid = isAndroid,
                    isTest = isTest,
                    isMultiplatform = isMultiplatform,
                    inheritedArgs = inheritedArgs,
                )
            }
            if (kc.useJdkRelease && this is KotlinJvmCompile) {
                when {
                    !isAndroid -> jvmTargetVersion?.let {
                        limitKotlinJdkApi(conf, it, inheritedArgs)
                    }

                    // Host tests run on a JDK, so they keep its API.
                    !isTest -> hideJdkFromAndroidCode(conf)
                }
            }
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

    // Targets fluxo didn't create (AGP 9's KMP plugin creates `android` itself; consumers may
    // declare targets directly) must honour `KMP_TARGETS` too, in every build, not only verbose.
    disableIfFilteredOut(conf)
}

internal fun KotlinProjectExtension.setupTargets(action: Action<in KotlinTarget>) {
    when (this) {
        is KotlinSingleTargetExtension<*> -> action.execute(target)
        is KotlinMultiplatformExtension -> targets.configureEach(action)
    }
}


private fun KotlinTarget.disableIfFilteredOut(conf: FluxoConfigurationExtensionImpl) {
    val target = this
    val project = conf.project
    val logger = project.logger
    val kmpTargetCode = KmpTargetCode.fromKotlinTarget(target, logger)
    if (kmpTargetCode == null || conf.ctx.isTargetEnabled(kmpTargetCode)) {
        return
    }

    logger.d("Target '{}' is not in KMP_TARGETS, disabling its compilations", target.name)
    conf.kmpTargetFilteredOut = true
    disableCompilations()
    disableDetektTasks(project)
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
