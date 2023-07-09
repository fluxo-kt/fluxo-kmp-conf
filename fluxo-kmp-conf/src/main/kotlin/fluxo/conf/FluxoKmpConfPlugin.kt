package fluxo.conf

import KMP_PLUGIN_ID
import fluxo.conf.data.BuildConstants.PLUGIN_ID
import fluxo.conf.deps.FluxoCache
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.container.impl.ContainerImpl
import fluxo.conf.dsl.container.impl.ContainerKotlinAware
import fluxo.conf.dsl.container.impl.ContainerKotlinMultiplatformAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
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
import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.configure
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.i
import fluxo.conf.impl.kotlin.setupKotlinExtension
import fluxo.conf.impl.libsCatalog
import fluxo.conf.impl.onVersion
import fluxo.conf.impl.withType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

@Suppress("unused", "EmptyFunctionBlock", "ktPropBy")
public class FluxoKmpConfPlugin : Plugin<Project> {

    // References:
    // https://docs.gradle.org/current/userguide/custom_gradle_types.html
    // https://docs.gradle.org/current/userguide/custom_plugins.html
    // https://github.com/diffplug/spotless/blob/main/plugin-gradle/src/main/java/com/diffplug/gradle/spotless/SpotlessPlugin.java

    override fun apply(target: Project) {
        target.checkIsRootProject("\"$PLUGIN_ID\" plugin")
        checkLifecycle(target)

        val context = FluxoKmpConfContext.getFor(target)

        target.allprojects {
            val configureContainers: ConfigureContainers = ::configureContainers
            extensions.create(
                FluxoConfigurationExtension::class.java,
                FluxoConfigurationExtensionImpl.NAME,
                FluxoConfigurationExtensionImpl::class.java,
                context,
                configureContainers,
            )

            // FIXME: Move to Kotlin configuration
            // Fix Kotlin/JS incompatibilities by pinning the versions of dependencies.
            // Workaround for https://youtrack.jetbrains.com/issue/KT-52776
            // Also see https://github.com/rjaros/kvision/blob/d9044ab/build.gradle.kts#L28
            afterEvaluate {
                plugins.withType<YarnPlugin> {
                    val libs = target.libsCatalog
                    target.configureExtension<YarnRootExtension> {
                        lockFileDirectory = target.rootDir.resolve(".kotlin-js-store")
                        libs.onVersion("js-engineIo") { resolution("engine.io", it) }
                        libs.onVersion("js-socketIo") { resolution("socket.io", it) }
                        libs.onVersion("js-uaParserJs") { resolution("ua-parser-js", it) }
                    }
                    target.configureExtension<NodeJsRootExtension> {
                        libs.onVersion("js-karma") { versions.karma.version = it }
                        libs.onVersion("js-mocha") { versions.mocha.version = it }
                        libs.onVersion("js-webpack") { versions.webpack.version = it }
                        libs.onVersion("js-webpackCli") { versions.webpackCli.version = it }
                        libs.onVersion("js-webpackDevServer") {
                            versions.webpackDevServer.version = it
                        }
                    }
                }
            }
        }

        FluxoCache.bindToProjectLifecycle(target)
        context.prepareCompleteKotlinPlugin()
        context.prepareGradleVersionsPlugin()
        context.prepareDependencyAnalysisPlugin()
        context.prepareDependencyGuardPlugin()
        context.prepareDependencyAnalysisTasks()
        context.prepareDependencyPinningBundle()
        context.prepareTaskTreePlugin()
        context.prepareTaskInfoPlugin()
        context.prepareKotlinSetupDiagnosticTasks()
        context.prepareModuleDependencyGraphPlugin()
        context.prepareBuildScanPlugin()
        context.ensureUnreachableTasksDisabled()
    }


    private fun configureContainers(
        type: ConfigurationType,
        configuration: FluxoConfigurationExtensionImpl,
        containers: Collection<ContainerImpl>,
    ) {
        when (type) {
            ConfigurationType.KOTLIN_MULTIPLATFORM ->
                configureKotlinMultiplatform(configuration, containers)

            ConfigurationType.ANDROID_LIB -> TODO()
            ConfigurationType.ANDROID_APP -> TODO()
            ConfigurationType.KOTLIN_JVM -> TODO()
            ConfigurationType.IDEA_PLUGIN -> TODO()
        }
    }

    private fun configureKotlinMultiplatform(
        configuration: FluxoConfigurationExtensionImpl,
        containers: Collection<ContainerImpl>,
    ) {
        val targets = containers.filterIsInstance<KmpTargetContainerImpl<*>>()
        val project = configuration.project
        val logger = project.logger
        val label = ":setupMultiplatform"
        if (targets.isEmpty()) {
            logger.i("$label - no applicable targets found, skipping module configuration")
            return
        } else {
            logger.i(label)
        }

        val pluginManager = project.pluginManager
        configuration.context.loadAndApplyPluginIfNotApplied(id = KMP_PLUGIN_ID, project = project)

        project.extensions.configure<KotlinMultiplatformExtension> {
            // Set settings before the containers so that they may be overridden if desired.
            setupKotlinExtension(configuration)

            for (container in containers) {
                container.applyPluginsWith(pluginManager)

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


    /**
     * Make sure there's a `clean`, and a `check` tasks in root project.
     *
     * @see org.gradle.api.plugins.BasePlugin ('base')
     * @see org.gradle.language.base.plugins.LifecycleBasePlugin
     */
    private fun checkLifecycle(target: Project) {
        target.pluginManager.apply(LifecycleBasePlugin::class.java)
    }
}
