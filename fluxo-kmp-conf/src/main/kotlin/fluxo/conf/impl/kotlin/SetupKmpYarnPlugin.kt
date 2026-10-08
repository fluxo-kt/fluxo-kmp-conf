package fluxo.conf.impl.kotlin

import fluxo.annotation.VersionGated
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.fluxoConfiguration
import fluxo.conf.impl.checkIsRootProject
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.logDependency
import fluxo.conf.impl.withType
import fluxo.log.FluxoProblem
import fluxo.log.l
import fluxo.log.reportProblem
import fluxo.vc.FluxoVersionCatalog
import fluxo.vc.onVersion
import java.io.File
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.targets.js.NpmPackageVersion
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
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
        val kgp = ctx.kotlinPluginVersion
        val root = this@afterEvaluate

        if (setupDependencies) {
            setYarnVersion(libs, kgp)
        }

        val yarnName = kgp.extensionName("kotlinYarn", ::yarnExtensionName)
        configureExtension<YarnRootExtension>(yarnName) {
            // Consumers commit `<root>/.kotlin-js-store/yarn.lock`; KGP's own default is
            // `<root>/kotlin-js-store`, so leaving it to KGP would orphan that lock file silently.
            // The Wasm lock stays at KGP's `<root>/kotlin-js-store/wasm` for the same reason:
            // fluxo never moved it, so that is where consumers commit it.
            setLockFileDirectory(rootDir.resolve(".kotlin-js-store"), ctx.kotlinPluginVersion)

            // yarn.lock is calculated differently without tests, ignore mismatch
            if (testsDisabled) {
                ignoreYarnLockChanges(ctx.kotlinPluginVersion)
            }

            if (!setupDependencies) {
                return@configureExtension
            }

            setFromCatalog(root, libs, "js-engineIo", "engine.io")
            setFromCatalog(root, libs, "js-socketIo", "socket.io")
            setFromCatalog(root, libs, "js-uaParserJs", "ua-parser-js")
        }

        val nodeJsName = kgp.extensionName("kotlinNodeJs", ::nodeJsExtensionName)
        configureExtension<NodeJsRootExtension>(nodeJsName) {
            val v = versions
            if (setupDependencies) {
                setFromCatalog(libs, "js-karma", v.karma)
                setFromCatalog(libs, "js-mocha", v.mocha)
                setFromCatalog(libs, "js-webpack", v.webpack)
                setFromCatalog(libs, "js-webpackCli", v.webpackCli)
                setFromCatalog(libs, "js-webpackDevServer", v.webpackDevServer)
            }
            // After the catalog, so it sees the version from there or from the consumer's DSL.
            warnIfNewerMochaMajor(v.mocha.version)
        }
    }
}

/**
 * The Yarn 1.x line is frozen; the public Provider-based version
 * setter lives on `YarnRootEnvSpec` (extension name "yarnSpec"),
 * not on the deprecated `var version: String` of `YarnRootExtension`.
 */
private fun Project.setYarnVersion(libs: FluxoVersionCatalog, kgp: KotlinVersion) {
    val name = kgp.extensionName("kotlinYarnSpec", ::yarnSpecExtensionName)
    configureExtension<YarnRootEnvSpec>(name) {
        val alias = "js-yarn"
        val wasSet = libs.onVersion(alias) {
            version.set(it)
            logDependency(KJS, "$alias:$it")
        }
        if (!wasSet) {
            val min = MIN_YARN
            val current = version.orNull
            if (current == null ||
                KotlinToolingVersion(current) < KotlinToolingVersion(min)
            ) {
                version.set(min)
                logDependency(KJS, "$alias:$min")
            }
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

/**
 * Kotlin's JS test reporter calls Mocha's base reporter without `new`, which a newer Mocha major
 * can reject (Mocha 12, see [pinBrowserTestMocha]), failing every Node and browser test run.
 * Compared with [kotlinMocha], so the warning stops once a Kotlin release ships a newer Mocha.
 */
private fun Project.warnIfNewerMochaMajor(mocha: String) {
    val kotlin = kotlinMocha
    val major = KotlinToolingVersion(kotlin).major
    if (KotlinToolingVersion(mocha).major <= major) return
    reportProblem(
        FluxoProblem.JS_TOOL_TOO_NEW,
        "Mocha $mocha is a newer major than Kotlin's own Mocha $kotlin: Kotlin's JS test " +
            "reporter can't run on it, so JS tests fail.",
        "Set the `js-mocha` catalog entry (or `versions.mocha.version` in Kotlin's Node.js " +
            "settings) to $major.x, or remove it.",
    )
}

/** [project] is passed in: `YarnRootExtension.project` doesn't exist on Kotlin 2.1. */
private fun YarnRootExtension.setFromCatalog(
    project: Project,
    libs: FluxoVersionCatalog,
    alias: String,
    path: String,
) {
    libs.onVersion(alias) {
        resolution(path, it)
        project.logDependency(KJS, "$path:$it")
    }
}

/**
 * Kotlin's Yarn and Node.js extension names are `const val`s on Kotlin 2.1 and companion getters
 * from 2.2, so a reference compiled against a newer Kotlin throws `NoSuchMethodError` on 2.1.
 * [kotlin21] is the constant's value there; [current] is referenced only on 2.2+.
 */
private inline fun KotlinVersion.extensionName(kotlin21: String, current: () -> String) =
    if (this >= KOTLIN_2_2) current() else kotlin21


private const val MIN_YARN = "1.22.19"

private const val KJS = "KotlinJS"

/** Same KGP 2.4.20 Provider-API split as [ignoreYarnLockChanges]. */
private fun YarnRootExtension.setLockFileDirectory(dir: File, kgp: KotlinVersion) {
    if (kgp >= KOTLIN_2_4_20) {
        setLockFileDirectoryProperty(dir)
    } else {
        @Suppress("DEPRECATION")
        lockFileDirectory = dir
    }
}

/**
 * Stops yarn.lock checks from failing or rewriting the lock file.
 *
 * The Provider-based properties appear in KGP 2.4.20, and the legacy setters are scheduled for
 * removal in KGP 2.7, so neither form covers the whole supported Kotlin range alone. The newer
 * symbols are referenced only inside their own branch, so older KGP never resolves them.
 */
private fun YarnRootExtension.ignoreYarnLockChanges(kgp: KotlinVersion) {
    if (kgp >= KOTLIN_2_4_20) {
        ignoreYarnLockChangesByProperties()
    } else {
        @Suppress("DEPRECATION")
        yarnLockMismatchReport = YarnLockMismatchReport.NONE
        @Suppress("DEPRECATION")
        yarnLockAutoReplace = false
        @Suppress("DEPRECATION")
        reportNewYarnLock = false
    }
}

@VersionGated
private fun YarnRootExtension.setLockFileDirectoryProperty(dir: File) =
    lockFileDirectoryProperty.set(dir)

@VersionGated
private fun YarnRootExtension.ignoreYarnLockChangesByProperties() {
    yarnLockMismatchReportProperty.set(YarnLockMismatchReport.NONE)
    yarnLockAutoReplaceProperty.set(false)
    reportNewYarnLockProperty.set(false)
}
