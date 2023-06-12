package fluxo.conf

import areComposeMetricsEnabled
import disableTests
import ensureUnreachableTasksDisabled
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.isRootProject
import fluxo.conf.impl.libsCatalog
import fluxo.conf.impl.onVersion
import fluxo.conf.impl.register
import fluxo.conf.impl.withType
import getValue
import isCI
import isDesugaringEnabled
import isMaxDebugEnabled
import isR8Disabled
import isRelease
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.diagnostics.DependencyReportTask
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import useKotlinDebug

@Suppress("unused", "EmptyFunctionBlock", "ktPropBy")
public class FluxoKmpConfPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        if (!target.isRootProject) {
            return
        }

        val logger = target.logger

        val isCI by target.isCI()
        if (isCI) logger.lifecycle("> Conf CI mode is enabled!")

        val isRelease by target.isRelease()
        if (isRelease) logger.lifecycle("> Conf RELEASE mode is enabled!")

        val useKotlinDebug by target.useKotlinDebug()
        if (useKotlinDebug) logger.warn("> Conf USE_KOTLIN_DEBUG mode is enabled!")

        val areComposeMetricsEnabled by target.areComposeMetricsEnabled()
        if (areComposeMetricsEnabled) {
            logger.lifecycle("> Conf COMPOSE_METRICS mode is enabled!")
        }

        val isR8Disabled by target.isR8Disabled()
        if (isR8Disabled) logger.warn("> Conf DISABLE_R8 mode is enabled!")

        val disableTests by target.disableTests()
        if (disableTests) logger.warn("> Conf DISABLE_TESTS mode is enabled!")

        val isMaxDebug by target.isMaxDebugEnabled()
        if (isMaxDebug) logger.warn("> Conf MAX_DEBUG mode is enabled!")

        val isDesugaringEnabled by target.isDesugaringEnabled()
        if (isDesugaringEnabled) logger.warn("> Conf DESUGARING mode is enabled!")

        // Environment logging
        run {
            val gradle = target.gradle.gradleVersion
            val java = System.getProperty("java.version")
            val cpus = Runtime.getRuntime().availableProcessors()
            logger.lifecycle("> Conf Gradle $gradle, JRE $java, $cpus CPUs")
        }

        target.subprojects {
            // Convenience task to print full dependencies tree for any module
            // Use `buildEnvironment` task for the report about plugins
            // https://docs.gradle.org/current/userguide/viewing_debugging_dependencies.html
            tasks.register<DependencyReportTask>("allDeps")
        }

        target.tasks.register<Task>("resolveDependencies") {
            group = "other"
            description = "Resolve and prefetch dependencies"
            doLast {
                target.allprojects.forEach { p ->
                    p.configurations.plus(p.buildscript.configurations)
                        .filter { it.isCanBeResolved }
                        .forEach {
                            try {
                                it.resolve()
                            } catch (_: Throwable) {
                            }
                        }
                }
            }
        }

        // Fix Kotlin/JS incompatibilities by pinning the versions of dependencies.
        // Workaround for https://youtrack.jetbrains.com/issue/KT-52776
        // Also see https://github.com/rjaros/kvision/blob/d9044ab/build.gradle.kts#L28
        target.allprojects {
            afterEvaluate {
                plugins.withType<org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin> {
                    val libs = target.libsCatalog
                    target.configureExtension<YarnRootExtension> {
                        lockFileDirectory = project.rootDir.resolve(".kotlin-js-store")
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

        // Run only for CI. Takes time and not so useful locally.
        if (isCI) {
            target.ensureUnreachableTasksDisabled()
        }
    }
}
