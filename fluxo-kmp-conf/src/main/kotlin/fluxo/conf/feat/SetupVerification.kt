@file:Suppress("ktPropBy", "UnstableApiUsage")

package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.ANDROID_APP_PLUGIN_ID
import fluxo.conf.impl.android.ANDROID_LIB_PLUGIN_ID
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.withAnyPlugin
import io.github.detekt.gradle.DetektKotlinCompilerPlugin
import io.gitlab.arturbosch.detekt.DetektPlugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// TODO: Test separate ktlint setup with gradle plugin

internal fun FluxoKmpConfContext.setupVerificationRoot() {
    val mergeLint = mergeLintTask
    val mergeDetekt = mergeDetektTask
    if (mergeLint != null && mergeDetekt != null) {
        rootProject.tasks.matching { it.name == CHECK_TASK_NAME }
            .configureEach { dependsOn(mergeDetekt, mergeLint) }
    }
}

internal fun Project.setupVerification(conf: FluxoConfigurationExtensionImpl) {
    val context = conf.context
    val testsDisabled = context.testsDisabled
    if (!testsDisabled) {
        if (!isRootProject && conf.enableSpotless == true) {
            setupSpotless(context)
        }

        // Detekt is always availabe in the classpath as it's a dependency.
        pluginManager.apply(
            when (conf.enableDetektCompilerPlugin) {
                true -> DetektKotlinCompilerPlugin::class.java
                else -> DetektPlugin::class.java
            }
        )
    }

    val ignoredBuildTypes = conf.noVerificationBuildTypes
    val ignoredFlavors = conf.noVerificationFlavors
    setupDetekt(conf, ignoredBuildTypes, ignoredFlavors)

    withAnyPlugin(ANDROID_LIB_PLUGIN_ID, ANDROID_APP_PLUGIN_ID) {
        setupAndroidLint(conf, ignoredBuildTypes, ignoredFlavors)
    }
}
