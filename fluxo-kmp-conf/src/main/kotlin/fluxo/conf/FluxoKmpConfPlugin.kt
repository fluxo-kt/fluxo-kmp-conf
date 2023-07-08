package fluxo.conf

import fluxo.conf.data.BuildConstants.PLUGIN_ID
import fluxo.conf.deps.FluxoCache
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.container.impl.ContainerImpl
import fluxo.conf.dsl.container.impl.ContainerKotlinMultiplatformAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
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
import fluxo.conf.impl.d
import fluxo.conf.impl.libsCatalog
import fluxo.conf.impl.onVersion
import fluxo.conf.impl.withType
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
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
            val configureContainers = { containers: Collection<ContainerImpl> ->
                configure(this, containers)
            }
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


    private fun configure(
        project: Project,
        containers: Collection<ContainerImpl>,
    ) {
        val targets = containers.filterIsInstance<KmpTargetContainerImpl<*>>()
        val logger = project.logger
        if (targets.isEmpty()) {
            logger.d("No applicable setup found, skipping module configuration")
            return
        }

        val pluginManager = project.pluginManager
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        logger.d("Applied KMP plugin!")

        project.extensions.configure<KotlinMultiplatformExtension> {
            for (container in containers) {
                container.applyPluginsWith(pluginManager)

                if (container is ContainerKotlinMultiplatformAware) {
                    container.setup(this)
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
