package fluxo.conf

import fluxo.conf.data.BuildConstants.PLUGIN_ID
import fluxo.conf.deps.FluxoCache
import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.container.impl.ContainerImpl
import fluxo.conf.dsl.container.impl.ContainerKotlinMultiplatformAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.impl.target.TargetAndroidNativeContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleIosContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleMacosContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleTvosContainer
import fluxo.conf.dsl.container.impl.target.TargetAppleWatchosContainer
import fluxo.conf.dsl.container.impl.target.TargetLinuxContainer
import fluxo.conf.dsl.container.impl.target.TargetMingwContainer
import fluxo.conf.dsl.container.impl.target.TargetWasmNativeContainer
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.feat.ensureUnreachableTasksDisabled
import fluxo.conf.feat.prepareBuildScanPlugin
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
import fluxo.conf.impl.v
import fluxo.conf.impl.withType
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_MAIN_SOURCE_SET_NAME
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet.Companion.COMMON_TEST_SOURCE_SET_NAME
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
                configure(this, containers, context)
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
        context: FluxoKmpConfContext,
    ) {
        val targets = containers.filterIsInstance<KotlinTargetContainerImpl<*>>()
        val logger = project.logger
        if (targets.isEmpty()) {
            logger.d("No applicable setup found, skipping module configuration")
            return
        }

        val pluginManager = project.pluginManager
        pluginManager.apply("org.jetbrains.kotlin.multiplatform")
        logger.d("Applied KMP plugin!")

        project.extensions.configure<KotlinMultiplatformExtension> {
            this.targets.all {
                logger.v("target added: $name")
            }
            this.sourceSets.all {
                logger.v("sourceSet added: $name")
            }

            // TODO: Allow configuration to opt-out of this
//            if (context.kotlinPluginVersion >= KOTLIN_1_8_20) {
//                @OptIn(ExperimentalKotlinGradlePluginApi::class)
//                setupTargetHierarchy()
//            } else {
//                sourceSets.setupIntermediateSourceSets(targets)
//            }

            for (container in containers) {
                logger.v("container: ${container.name}")

                container.applyPluginsWith(pluginManager)

                if (container is ContainerKotlinMultiplatformAware) {
                    container.setup(this)
                }
            }
        }
    }

    private fun NamedDomainObjectContainer<KotlinSourceSet>.setupIntermediateSourceSets(targets: List<KotlinTargetContainerImpl<*>>) {
        val commonMain = getByName(COMMON_MAIN_SOURCE_SET_NAME)
        val commonTest = getByName(COMMON_TEST_SOURCE_SET_NAME)

        val jvmTargets = targets.filterIsInstance<KotlinTargetContainerImpl.CommonJvm<*>>()
        if (jvmTargets.isNotEmpty()) {
            maybeCreate("${KotlinTargetContainerImpl.CommonJvm.COMMON_JVM}Main").apply {
                dependsOn(commonMain)
            }
            maybeCreate("${KotlinTargetContainerImpl.CommonJvm.COMMON_JVM}Test").apply {
                dependsOn(commonTest)
            }
        }

        val nonJvmTargets = targets.filterIsInstance<KotlinTargetContainerImpl.NonJvm<*>>()
        if (nonJvmTargets.isEmpty()) return
        val nonJvmMain = maybeCreate("${KotlinTargetContainerImpl.NonJvm.NON_JVM}Main").apply {
            dependsOn(commonMain)
        }
        val nonJvmTest = maybeCreate("${KotlinTargetContainerImpl.NonJvm.NON_JVM}Test").apply {
            dependsOn(commonTest)
        }

        val nativeTargets =
            nonJvmTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native<*>>()
        if (nativeTargets.isEmpty()) return
        val nativeMain =
            maybeCreate("${KotlinTargetContainerImpl.NonJvm.Native.NATIVE}Main").apply {
                dependsOn(nonJvmMain)
            }
        val nativeTest =
            maybeCreate("${KotlinTargetContainerImpl.NonJvm.Native.NATIVE}Test").apply {
                dependsOn(nonJvmTest)
            }

        val androidNativeTargets =
            nativeTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.AndroidNative>()
        if (androidNativeTargets.isNotEmpty()) {
            maybeCreate("${TargetAndroidNativeContainer.ANDROID_NATIVE}Main").apply {
                dependsOn(nativeMain)
            }
            maybeCreate("${TargetAndroidNativeContainer.ANDROID_NATIVE}Test").apply {
                dependsOn(nativeTest)
            }
        }

        val unixTargets =
            nativeTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix<*>>()
        if (unixTargets.isNotEmpty()) {
            val unixMain =
                maybeCreate("${KotlinTargetContainerImpl.NonJvm.Native.Unix.UNIX}Main").apply {
                    dependsOn(nativeMain)
                }
            val unixTest =
                maybeCreate("${KotlinTargetContainerImpl.NonJvm.Native.Unix.UNIX}Test").apply {
                    dependsOn(nativeTest)
                }

            val appleTargets =
                unixTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple<*>>()
            if (appleTargets.isNotEmpty()) {
                val darwinMain =
                    maybeCreate("${KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.APPLE}Main").apply {
                        dependsOn(unixMain)
                    }
                val darwinTest =
                    maybeCreate("${KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.APPLE}Test").apply {
                        dependsOn(unixTest)
                    }

                val iosTargets =
                    appleTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.Ios<*>>()
                if (iosTargets.isNotEmpty()) {
                    maybeCreate("${TargetAppleIosContainer.IOS}Main").apply {
                        dependsOn(darwinMain)
                    }
                    maybeCreate("${TargetAppleIosContainer.IOS}Test").apply {
                        dependsOn(darwinTest)
                    }
                }

                val macosTargets =
                    appleTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.Macos>()
                if (macosTargets.isNotEmpty()) {
                    maybeCreate("${TargetAppleMacosContainer.MACOS}Main").apply {
                        dependsOn(darwinMain)
                    }
                    maybeCreate("${TargetAppleMacosContainer.MACOS}Test").apply {
                        dependsOn(darwinTest)
                    }
                }

                val tvosTargets =
                    appleTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.Tvos<*>>()
                if (tvosTargets.isNotEmpty()) {
                    maybeCreate("${TargetAppleTvosContainer.TVOS}Main").apply {
                        dependsOn(darwinMain)
                    }
                    maybeCreate("${TargetAppleTvosContainer.TVOS}Test").apply {
                        dependsOn(darwinTest)
                    }
                }

                val watchosTargets =
                    appleTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.Watchos<*>>()
                if (watchosTargets.isNotEmpty()) {
                    maybeCreate("${TargetAppleWatchosContainer.WATCHOS}Main").apply {
                        dependsOn(darwinMain)
                    }
                    maybeCreate("${TargetAppleWatchosContainer.WATCHOS}Test").apply {
                        dependsOn(darwinTest)
                    }
                }
            }

            val linuxTargets =
                unixTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Unix.Linux<*>>()
            if (linuxTargets.isNotEmpty()) {
                maybeCreate("${TargetLinuxContainer.LINUX}Main").dependsOn(unixMain)
                maybeCreate("${TargetLinuxContainer.LINUX}Test").dependsOn(unixTest)
            }
        }

        val mingwTargets =
            nativeTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.Mingw<*>>()
        if (mingwTargets.isNotEmpty()) {
            maybeCreate("${TargetMingwContainer.MINGW}Main").dependsOn(nativeMain)
            maybeCreate("${TargetMingwContainer.MINGW}Test").dependsOn(nativeTest)
        }

        val wasmNativeTargets =
            nativeTargets.filterIsInstance<KotlinTargetContainerImpl.NonJvm.Native.WasmNative>()
        if (wasmNativeTargets.isNotEmpty()) {
            maybeCreate("${TargetWasmNativeContainer.WASM_NATIVE}Main").dependsOn(nativeMain)
            maybeCreate("${TargetWasmNativeContainer.WASM_NATIVE}Test").dependsOn(nativeTest)
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
