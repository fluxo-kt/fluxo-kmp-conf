package fluxo.conf.impl.kotlin

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.l
import fluxo.conf.impl.onVersion
import fluxo.conf.impl.withType
import org.gradle.api.Project
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
            if (setupDependencies && libs?.onVersion("js-yarn") { version = it } != true) {
                if (KotlinToolingVersion(version) < KotlinToolingVersion("1.22.19")) {
                    version = "1.22.19"
                }
            }

            // yarn.lock is calculated differently without tests, ignore mismatch
            if (testsDisabled) {
                yarnLockMismatchReport = YarnLockMismatchReport.NONE
                yarnLockAutoReplace = false
                reportNewYarnLock = false
            }

            if (libs == null || !setupDependencies) {
                return@configureExtension
            }

            libs.onVersion("js-engineIo") { resolution("engine.io", it) }
            libs.onVersion("js-socketIo") { resolution("socket.io", it) }
            libs.onVersion("js-uaParserJs") { resolution("ua-parser-js", it) }
        }

        if (libs == null || !setupDependencies) {
            return@configuration
        }
        configureExtension<NodeJsRootExtension>(NodeJsRootExtension.EXTENSION_NAME) {
            val v = versions
            libs.onVersion("js-karma") { v.karma.version = it }
            libs.onVersion("js-mocha") { v.mocha.version = it }
            libs.onVersion("js-webpack") { v.webpack.version = it }
            libs.onVersion("js-webpackCli") { v.webpackCli.version = it }
            libs.onVersion("js-webpackDevServer") { v.webpackDevServer.version = it }
        }
    }
}
