package fluxo.conf.feat

import com.autonomousapps.DependencyAnalysisExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEPS_ANALYSIS_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.DEPS_ANALYSIS_PLUGIN_ID
import fluxo.conf.data.BuildConstants.DEPS_ANALYSIS_PLUGIN_VERSION
import fluxo.conf.data.VersionCatalogConstants.VC_ANDROIDX_COMPOSE_TOOLING_ALIAS
import fluxo.conf.data.VersionCatalogConstants.VC_SQUARE_LEAK_CANARY_ALIAS
import fluxo.conf.data.VersionCatalogConstants.VC_SQUARE_PLUMBER_ALIAS
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.impl.configureExtension
import fluxo.conf.impl.onLibrary

// Detect unused and misused dependencies.
// Provides advice for managing dependencies and other applied plugins.
// https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/blob/main/CHANGELOG.md
// https://plugins.gradle.org/plugin/com.autonomousapps.dependency-analysis
// https://mvnrepository.com/artifact/com.autonomousapps/dependency-analysis-gradle-plugin
internal fun FluxoKmpConfContext.prepareDependencyAnalysisPlugin() {
    if (!hasStartTaskCalled(DEPS_ANALYSIS_TASK_NAMES)) {
        return
    }

    loadAndApplyPluginIfNotApplied(
        id = DEPS_ANALYSIS_PLUGIN_ID,
        className = DEPS_ANALYSIS_CLASS_NAME,
        version = DEPS_ANALYSIS_PLUGIN_VERSION,
        catalogPluginId = DEPS_ANALYSIS_PLUGIN_ALIAS,
    )

    rootProject.configureExtension<DependencyAnalysisExtension> {
        // https://github.com/autonomousapps/dependency-analysis-android-gradle-plugin/wiki/Customizing-plugin-behavior
        structure {
            bundle("kotlin-stdlib") {
                includeGroup("org.jetbrains.kotlin")
            }
            bundle("androidx-lifecycle") {
                includeGroup("androidx.lifecycle")
            }
            bundle("accompanist") {
                includeGroup("com.google.accompanist")
            }
        }
        issues {
            all {
                onIncorrectConfiguration {
                    severity("fail")
                }
                onUnusedDependencies {
                    severity("fail")
                    // Needed for Compose '@Preview'
                    libs?.onLibrary(VC_ANDROIDX_COMPOSE_TOOLING_ALIAS, ::exclude)
                }
                onAny {
                    // Auto used, no code references
                    libs?.onLibrary(VC_SQUARE_LEAK_CANARY_ALIAS, ::exclude)
                    libs?.onLibrary(VC_SQUARE_PLUMBER_ALIAS, ::exclude)
                }
            }

            // TODO: no need to declare transitive dependencies for the final app module
            if (false) {
                project(":app") {
                    onUsedTransitiveDependencies {
                        severity("ignore")
                    }
                }
            }
        }
    }
}

private val DEPS_ANALYSIS_TASK_NAMES = arrayOf(
    // public plugin tasks
    "buildHealth",
    "projectHealth",
    "reason",
    // internal plugin tasks
    // "computeDuplicateDependencies", "printDuplicateDependencies", "postProcess",
)

/** @see com.autonomousapps.DependencyAnalysisPlugin */
private const val DEPS_ANALYSIS_CLASS_NAME = "com.autonomousapps.DependencyAnalysisPlugin"
