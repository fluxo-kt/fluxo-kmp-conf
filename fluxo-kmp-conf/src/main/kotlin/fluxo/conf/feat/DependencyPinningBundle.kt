package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.VersionCatalogConstants.VC_PINNED_BUNDLE_ALIAS
import fluxo.conf.impl.b
import fluxo.conf.impl.d

internal fun FluxoKmpConfContext.prepareDependencyPinningBundle() {
    val alias = VC_PINNED_BUNDLE_ALIAS
    val bundle = libs.b(alias)?.orNull ?: return
    if (bundle.isEmpty()) {
        return
    }
    rootProject.logger.d("Pinning ${bundle.size} dependencies from bundle '$alias'")
    val pinnedDeps = bundle.associate {
        it.module to it.versionConstraint.toString()
    }
    rootProject.allprojects {
        configurations.all {
            resolutionStrategy.eachDependency {
                val version = pinnedDeps[requested.module]
                if (version != null) {
                    useVersion(version)
                    because(PIN_REASON)
                }
            }
        }
    }
}

private const val PIN_REASON = "Pinned due to security recommendations or other considerations"
