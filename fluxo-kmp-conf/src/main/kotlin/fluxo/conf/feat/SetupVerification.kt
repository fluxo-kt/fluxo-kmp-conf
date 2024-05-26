@file:Suppress("ktPropBy", "UnstableApiUsage")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.namedCompat
import fluxo.conf.impl.withAnyPlugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// TODO: Test separate ktlint setup with gradle plugin

internal fun FluxoKmpConfContext.setupVerificationRoot() {
    val mergeLint = mergeLintTask
    val mergeDetekt = mergeDetektTask
    if (mergeLint != null && mergeDetekt != null) {
        rootProject.tasks.namedCompat { it == CHECK_TASK_NAME }
            .configureEach { dependsOn(mergeDetekt, mergeLint) }
    }
}

internal fun Project.setupVerification(conf: FluxoConfigurationExtensionImpl) {
    val ctx = conf.ctx
    val testsDisabled = !conf.setupVerification || ctx.testsDisabled

    val ignoredBuildTypes = conf.noVerificationBuildTypes
    val ignoredFlavors = conf.noVerificationFlavors
    setupDetekt(conf, ignoredBuildTypes, ignoredFlavors, testsDisabled)

    withAnyPlugin(ANDROID_LIB_PLUGIN_ID, ANDROID_APP_PLUGIN_ID) {
        setupAndroidLint(conf, ignoredBuildTypes, ignoredFlavors, testsDisabled)
    }

    if (testsDisabled) {
        return
    }

    if (!isRootProject && conf.enableSpotless) {
        setupSpotless(ctx)
    }

    when (conf.mode) {
        ConfigurationType.KOTLIN_MULTIPLATFORM,
        ConfigurationType.KOTLIN_JVM,
        ConfigurationType.GRADLE_PLUGIN,
        ConfigurationType.IDEA_PLUGIN,
        -> {
            val enableGenericAndroidLint = conf.enableGenericAndroidLint

            // If KMP has an Android target, Lint will be setup by the Android plugin.
            val isNotKmpWithAndroid = conf.mode != ConfigurationType.KOTLIN_MULTIPLATFORM ||
                !conf.ctx.isTargetEnabled(KmpTargetCode.ANDROID)

            // FIXME: Setup Android Lint for non-Android targets in KMP
            if (enableGenericAndroidLint && isNotKmpWithAndroid) {
                setupAndroidLintPluginWithNoAndroid(conf)
            }
        }

        else -> {
            // Do nothing
        }
    }
}
