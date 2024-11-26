package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.VersionCatalogConstants.VC_PINNED_BUNDLE_ALIAS
import fluxo.conf.impl.kotlin.KOTLIN_2_1
import fluxo.conf.impl.kotlin.KOTLIN_PLUGIN_VERSION_STRING
import fluxo.conf.impl.logDependency
import fluxo.log.d
import fluxo.log.l
import fluxo.log.v
import fluxo.vc.b
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.logging.Logger

/** Pair is (version, reason) */
private typealias PinnedDeps = HashMap<ModuleIdentifier, Pair<String, String>>


internal fun FluxoKmpConfContext.prepareDependencyPinningBundle() {
    val libs = libs.gradle ?: return
    val p = rootProject

    val pinnedDeps: PinnedDeps = HashMap()

    if (kotlinPluginVersion >= KOTLIN_2_1) {
        // KGP doesn't depend on the `kotlin-compiler-embeddable` dependency
        // starting from Kotlin 2.1.0
        // Other plugins can bring incompatible versions of the compiler.
        // https://kotlinlang.slack.com/archives/C0KLZSCHF/p1729256644747559?thread_ts=1729151089.194689&cid=C0KLZSCHF
        val compilerEmbeddable = object : ModuleIdentifier {
            override fun getGroup() = "org.jetbrains.kotlin"
            override fun getName() = "kotlin-compiler-embeddable"
        }
        val version = KOTLIN_PLUGIN_VERSION_STRING
        pinnedDeps[compilerEmbeddable] = Pair(version, "Pinned to Kotlin plugin version")
    }

    val bundleAliases = libs.bundleAliases
    if (bundleAliases.isNotEmpty()) {
        for (alias in bundleAliases) {
            collectPinnedDependencies(alias, p.logger, pinnedDeps)
        }
    }
    if (pinnedDeps.isEmpty()) {
        p.logger.d("No dependencies pinned by version catalog bundles")
        return
    }

    pinDependencies(pinnedDeps, project = p)
    p.subprojects {
        pinDependencies(pinnedDeps, project = this)
    }
}

private fun pinDependencies(
    pinnedDeps: PinnedDeps,
    project: Project,
) {
    project.buildscript.configurations.configureEach {
        pinDependencies(pinnedDeps, project, conf = this)
    }
    project.configurations.configureEach {
        pinDependencies(pinnedDeps, project, conf = this)
    }
}

private fun pinDependencies(
    pinnedDeps: PinnedDeps,
    project: Project,
    conf: Configuration,
) {
    val path = project.path + "::${conf.name}"
    conf.resolutionStrategy.eachDependency d@{
        val module = requested.module
        val (version, reason) = pinnedDeps[module] ?: return@d
        if (DEBUG_PINS) {
            project.logger.v("Pinning ${requested.module} to $version in $path")
        }
        useVersion(version)
        because(reason)
    }
}


private fun FluxoKmpConfContext.collectPinnedDependencies(
    alias: String,
    logger: Logger,
    pinnedDeps: PinnedDeps,
) {
    // Filter "pinned" and "pinned.*" bundles
    alias.startsWith(ALIAS, ignoreCase = true) && alias.run {
        val l = length
        l == ALIAS.length || l > ALIAS.length && this[ALIAS.length] == '.'
    } || return

    val bundle = libs.b(alias)?.get()
    if (bundle.isNullOrEmpty()) {
        return
    }
    logger.l("Pinning ${bundle.size} dependencies from version catalog bundle '$alias'")

    val reason = "$PIN_REASON from bundle '$alias'"
    for (dep in bundle) {
        logger.logDependency("pinned", dep)
        pinnedDeps[dep.module] = Pair(dep.versionConstraint.toString(), reason)
    }
}

private const val DEBUG_PINS = false

private const val ALIAS = VC_PINNED_BUNDLE_ALIAS

private const val PIN_REASON = "Pinned due to security recommendations or other considerations"
