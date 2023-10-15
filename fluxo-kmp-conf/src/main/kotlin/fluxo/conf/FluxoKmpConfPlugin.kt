package fluxo.conf

import fluxo.conf.data.BuildConstants.PLUGIN_ID
import fluxo.conf.deps.FluxoCache
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.impl.ConfigureContainers
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl.ConfigurationType
import fluxo.conf.feat.ensureUnreachableTasksDisabled
import fluxo.conf.feat.prepareBuildScanPlugin
import fluxo.conf.feat.prepareCompleteKotlinPlugin
import fluxo.conf.feat.prepareDependencyAnalysisPlugin
import fluxo.conf.feat.prepareDependencyAnalysisTasks
import fluxo.conf.feat.prepareDependencyGuardPlugin
import fluxo.conf.feat.prepareDependencyPinningBundle
import fluxo.conf.feat.prepareGradleVersionsPlugin
import fluxo.conf.feat.prepareKotlinSetupDiagnosticTasks
import fluxo.conf.feat.prepareModuleDependencyGraphPlugin
import fluxo.conf.feat.prepareTaskInfoPlugin
import fluxo.conf.feat.prepareTaskTreePlugin
import fluxo.conf.feat.setupSpotless
import fluxo.conf.feat.setupTestsReport
import fluxo.conf.feat.setupVerificationRoot
import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.kotlin.configureKotlinJvm
import fluxo.conf.impl.kotlin.configureKotlinMultiplatform
import fluxo.conf.impl.kotlin.setupKmpYarnPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

@Suppress("unused", "EmptyFunctionBlock", "ktPropBy")
public class FluxoKmpConfPlugin : Plugin<Project> {

    // References:
    // https://docs.gradle.org/current/userguide/custom_gradle_types.html
    // https://docs.gradle.org/current/userguide/custom_plugins.html
    // https://github.com/diffplug/spotless/blob/main/plugin-gradle/src/main/java/com/diffplug/gradle/spotless/SpotlessPlugin.java

    override fun apply(target: Project) {
        target.checkIsRootProject("\"$PLUGIN_ID\" plugin")
        checkGradleLifecycleBase(target)
        checkKotlinPlugin()

        val context = FluxoKmpConfContext.getFor(target)

        target.allprojects {
            val configureContainers: ConfigureContainers = ::configureContainers
            val conf = extensions.create(
                FluxoConfigurationExtension::class.java,
                FluxoConfigurationExtension.NAME,
                FluxoConfigurationExtensionImpl::class.java,
                context,
                configureContainers,
            )

            if (isRootProject && !context.testsDisabled) {
                afterEvaluate {
                    val enableVerification = conf.enableVerification == true
                    if (enableVerification && conf.enableSpotless == true) {
                        target.setupSpotless(context)
                    }
                }
            }
        }

        target.setupKmpYarnPlugin(context)

        FluxoCache.bindToProjectLifecycle(target)
        context.prepareDependencyPinningBundle()
        context.prepareBuildScanPlugin()
        context.ensureUnreachableTasksDisabled()

        if (context.testsDisabled) {
            return
        }

        context.prepareCompleteKotlinPlugin()
        context.prepareGradleVersionsPlugin()
        context.prepareDependencyAnalysisPlugin()
        context.prepareDependencyGuardPlugin()
        context.prepareDependencyAnalysisTasks()
        context.prepareTaskTreePlugin()
        context.prepareTaskInfoPlugin()
        context.prepareKotlinSetupDiagnosticTasks()
        context.prepareModuleDependencyGraphPlugin()
        context.setupTestsReport()
        context.setupVerificationRoot()
    }

    private fun configureContainers(
        type: ConfigurationType,
        configuration: FluxoConfigurationExtensionImpl,
        containers: Collection<Container>,
    ) {
        val containerArray = containers.toTypedArray()
        when (type) {
            ConfigurationType.KOTLIN_MULTIPLATFORM ->
                configureKotlinMultiplatform(configuration, containerArray)

            else ->
                configureKotlinJvm(type, configuration, containerArray)
        }
    }

    /**
     * Make sure there's a `clean`, and a `check` tasks in root project.
     *
     * @see org.gradle.api.plugins.BasePlugin ('base')
     * @see org.gradle.language.base.plugins.LifecycleBasePlugin
     */
    private fun checkGradleLifecycleBase(target: Project) {
        target.pluginManager.apply(LifecycleBasePlugin::class.java)
    }

    /**
     * Make sure there's a Kotlin plugin in the classpath.
     *
     * @see org.jetbrains.kotlin.gradle.plugin.mpp.KotlinMultiplatformPlugin
     * @see org.jetbrains.kotlin.gradle.plugin.AbstractKotlinPlugin
     */
    private fun checkKotlinPlugin() {
        try {
            KotlinVersion.DEFAULT
        } catch (e: Throwable) {
            val msg = """
                Kotlin plugin not found in classpath.
                Please apply any Kotlin plugin before applying the "$PLUGIN_ID" plugin.
                Example:
                ```
                plugins {
                    id("org.jetbrains.kotlin.multiplatform") apply false
                    id("$PLUGIN_ID")
                }
                ```
            """.trimIndent()
            throw GradleException(msg, e)
        }
    }
}
