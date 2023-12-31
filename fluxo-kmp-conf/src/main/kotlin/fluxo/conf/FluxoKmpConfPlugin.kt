package fluxo.conf

import fluxo.conf.data.BuildConstants.PLUGIN_ID
import fluxo.conf.deps.FluxoCache
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.container.Container
import fluxo.conf.dsl.impl.ConfigurationType
import fluxo.conf.dsl.impl.ConfigureContainers
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.ensureUnreachableTasksDisabled
import fluxo.conf.feat.prepareBuildConfigKmpPlugin
import fluxo.conf.feat.prepareBuildScanPlugin
import fluxo.conf.feat.prepareCompleteKotlinPlugin
import fluxo.conf.feat.prepareDependencyAnalysisPlugin
import fluxo.conf.feat.prepareDependencyAnalysisTasks
import fluxo.conf.feat.prepareDependencyGuardPlugin
import fluxo.conf.feat.prepareDependencyPinningBundle
import fluxo.conf.feat.prepareDependencyUpdatesPlugin
import fluxo.conf.feat.prepareKotlinSetupDiagnosticTasks
import fluxo.conf.feat.prepareModuleDependencyGraphPlugin
import fluxo.conf.feat.prepareTaskInfoPlugin
import fluxo.conf.feat.prepareTaskTreePlugin
import fluxo.conf.feat.setupBinaryCompatibilityValidator
import fluxo.conf.feat.setupGradleProjectPublication
import fluxo.conf.feat.setupSpotless
import fluxo.conf.feat.setupTestsReport
import fluxo.conf.feat.setupVerificationRoot
import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.kotlin.KOTLIN_EXT
import fluxo.conf.impl.kotlin.configureKotlinJvm
import fluxo.conf.impl.kotlin.configureKotlinMultiplatform
import fluxo.conf.impl.kotlin.setupKmpYarnPlugin
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

public class FluxoKmpConfPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        target.checkIsRootProject("\"$PLUGIN_ID\" plugin")
        checkGradleLifecycleBase(target)
        checkKotlinPlugin()

        val ctx = FluxoKmpConfContext.getFor(target)

        target.allprojects {
            val configureContainers: ConfigureContainers = ::configureContainers
            val conf = extensions.create(
                FluxoConfigurationExtension::class.java,
                FluxoConfigurationExtension.NAME,
                FluxoConfigurationExtensionImpl::class.java,
                ctx,
                configureContainers,
            )

            if (isRootProject && !ctx.testsDisabled) {
                afterEvaluate {
                    val enableVerification = conf.enableVerification == true
                    if (enableVerification && conf.enableSpotless == true) {
                        target.setupSpotless(ctx)
                    }
                }
            }

            ctx.prepareBuildConfigKmpPlugin(project = this)
        }

        target.setupKmpYarnPlugin(ctx)

        FluxoCache.bindToProjectLifecycle(target)
        ctx.prepareDependencyPinningBundle()
        ctx.prepareBuildScanPlugin()
        ctx.ensureUnreachableTasksDisabled()

        if (ctx.testsDisabled || ctx.isInCompositeBuild) {
            return
        }

        ctx.prepareCompleteKotlinPlugin()
        ctx.prepareDependencyUpdatesPlugin()
        ctx.prepareDependencyAnalysisPlugin()
        ctx.prepareDependencyGuardPlugin()
        ctx.prepareDependencyAnalysisTasks()
        ctx.prepareTaskTreePlugin()
        ctx.prepareTaskInfoPlugin()
        ctx.prepareKotlinSetupDiagnosticTasks()
        ctx.prepareModuleDependencyGraphPlugin()
        ctx.setupTestsReport()
        ctx.setupVerificationRoot()
    }

    /**
     *
     * @see FluxoConfigurationExtensionImpl.configureAs
     * @see ConfigureContainers
     */
    private fun configureContainers(
        conf: FluxoConfigurationExtensionImpl,
        containers: Collection<Container>,
    ) {
        val containerArray = containers.toTypedArray()
        val configured = when (conf.mode) {
            ConfigurationType.KOTLIN_MULTIPLATFORM ->
                configureKotlinMultiplatform(conf, containerArray)

            else ->
                configureKotlinJvm(conf, containerArray)
        }
        if (!configured) {
            return
        }

        // Public API validation
        if (conf.enableApiValidation != false) {
            setupBinaryCompatibilityValidator(conf.apiValidation, conf)
        }

        // Gradle project atifacts publication
        setupGradleProjectPublication(conf)

        // Generic custom lazy configuration
        conf.onConfiguration?.let { action ->
            conf.project.configureExtension(KOTLIN_EXT, action = action)
        }
    }

    /**
     * Make sure there's a `clean`, and a `check` tasks in root project.
     *
     * @see org.gradle.api.plugins.BasePlugin ('base')
     * @see org.gradle.language.base.plugins.LifecycleBasePlugin
     */
    private fun checkGradleLifecycleBase(target: Project) =
        target.pluginManager.apply(LifecycleBasePlugin::class.java)

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
            // TODO: Support version catalog declarations if available
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
