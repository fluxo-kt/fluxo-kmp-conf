@file:Suppress("UnusedPrivateMember")

package fluxo.conf.impl.kotlin

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.logDependency
import fluxo.conf.impl.withType
import fluxo.log.l
import fluxo.vc.FluxoVersionCatalog
import fluxo.vc.onVersion
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.targets.js.NpmPackageVersion
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion

// Fix Kotlin/JS incompatibilities by pinning the versions of dependencies.
// Workaround for https://youtrack.jetbrains.com/issue/KT-52776.
// Also see https://github.com/rjaros/kvision/blob/d9044ab/build.gradle.kts#L28
internal fun Project.setupKmpYarnPlugin(ctx: FluxoKmpConfContext) = afterEvaluate {
    // YarnPlugin can be applied only to the root project.
    checkIsRootProject("setupKmpYarnPlugin")
    plugins.withType<YarnPlugin> configuration@{
        val conf = fluxoConfiguration
        @Suppress("DEPRECATION")
        if (conf?.setupKotlin != true) {
            logger.l("YarnPlugin configuration disabled!")
            return@configuration
        }

        logger.l("YarnPlugin configuration")
        val setupDependencies = conf.setupDependencies

        val libs = ctx.libs
        val testsDisabled = ctx.testsDisabled
        configureExtension<YarnRootExtension>(YarnRootExtension.YARN) {
            lockFileDirectory = rootDir.resolve(".kotlin-js-store")

            // The Yarn 1.x line is frozen.
            // At least use the last known version.
            // https://github.com/yarnpkg/yarn/releases.
            if (setupDependencies) {
                val alias = "js-yarn"
                val wasSet = libs.onVersion(alias) {
                    version = it
                    logDependency(KJS, "$alias:$it")
                }
                if (!wasSet) {
                    val min = MIN_YARN
                    if (KotlinToolingVersion(version) < KotlinToolingVersion(min)) {
                        version = min
                        logDependency(KJS, "$alias:$min")
                    }
                }
            }

            // yarn.lock is calculated differently without tests, ignore mismatch
            if (testsDisabled) {
                yarnLockMismatchReport = YarnLockMismatchReport.NONE
                yarnLockAutoReplace = false
                reportNewYarnLock = false
            }

            if (!setupDependencies) {
                return@configureExtension
            }

            setFromCatalog(libs, "js-engineIo", "engine.io")
            setFromCatalog(libs, "js-socketIo", "socket.io")
            setFromCatalog(libs, "js-uaParserJs", "ua-parser-js")
        }

        if (!setupDependencies) {
            return@configuration
        }
        configureExtension<NodeJsRootExtension>(NodeJsRootExtension.EXTENSION_NAME) {
            val v = versions
            setFromCatalog(libs, "js-karma", v.karma)
            setFromCatalog(libs, "js-mocha", v.mocha)
            setFromCatalog(libs, "js-webpack", v.webpack)
            setFromCatalog(libs, "js-webpackCli", v.webpackCli)
            setFromCatalog(libs, "js-webpackDevServer", v.webpackDevServer)
        }
    }
}

private fun Project.setFromCatalog(
    libs: FluxoVersionCatalog,
    alias: String,
    npv: NpmPackageVersion,
) {
    libs.onVersion(alias) {
        if (KotlinToolingVersion(npv.version) < KotlinToolingVersion(it)) {
            npv.version = it
            logDependency(KJS, "${npv.name}:$it")
        }
    }
}

private fun YarnRootExtension.setFromCatalog(
    libs: FluxoVersionCatalog,
    alias: String,
    path: String,
) {
    libs.onVersion(alias) {
        resolution(path, it)
        project.logDependency(KJS, "$path:$it")
    }
}


private const val MIN_YARN = "1.22.19"

private const val KJS = "KotlinJS"
