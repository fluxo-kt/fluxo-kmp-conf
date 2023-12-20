package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.VersionCatalogConstants.VC_PINNED_BUNDLE_ALIAS
import fluxo.conf.impl.b
import fluxo.conf.impl.d
import fluxo.conf.impl.l
import fluxo.conf.impl.logDependency
import org.gradle.api.artifacts.ModuleIdentifier

internal fun FluxoKmpConfContext.prepareDependencyPinningBundle() {
    val libs = libs ?: return
    val logger = rootProject.logger

    val pinnedDeps = HashMap<ModuleIdentifier, Pair<String, String>>()
    val bundleAliases = libs.bundleAliases
    if (bundleAliases.isNotEmpty()) for (alias in bundleAliases) {
        // Filter "pinned and "pinned.*" bundles
        alias.startsWith(ALIAS, ignoreCase = true) && alias.run {
            val l = length
            l == ALIAS.length || l > ALIAS.length && this[ALIAS.length] == '.'
        } || continue

        val bundle = libs.b(alias)?.get()
        if (bundle.isNullOrEmpty()) {
            continue
        }
        logger.l("Pinning ${bundle.size} dependencies from version catalog bundle '$alias'")

        val reason = "$PIN_REASON from bundle '$alias'"
        for (dep in bundle) {
            val constraint = dep.versionConstraint.toString()
            with(rootProject) {
                logDependency("pinned", dep, " ('$alias' constraint)")
            }
            pinnedDeps[dep.module] = Pair(constraint, reason)
        }
    }

    if (pinnedDeps.isEmpty()) {
        logger.d("No dependencies pinned by version catalog bundles")
        return
    }

    rootProject.allprojects {
        configurations.configureEach {
            resolutionStrategy.eachDependency d@{
                val (version, reason) = pinnedDeps[requested.module] ?: return@d
                useVersion(version)
                because(reason)
            }
        }
    }
}

private const val ALIAS = VC_PINNED_BUNDLE_ALIAS

private const val PIN_REASON = "Pinned due to security recommendations or other considerations"
