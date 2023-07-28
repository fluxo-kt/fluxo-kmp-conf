package fluxo.conf.feat

import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardConfiguration
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ID
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.impl.configureExtension
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// Plugin that guards against unintentional dependency changes
// https://github.com/dropbox/dependency-guard/blob/main/CHANGELOG.md#change-log
// https://mvnrepository.com/artifact/com.dropbox.dependency-guard/dependency-guard
internal fun FluxoKmpConfContext.prepareDependencyGuardPlugin() {
    val isCalled = hasStartTaskCalled(DEPS_GUARD_TASK_NAMES)
    if (isCalled) {
        markProjectInSync()
    }
    onProjectInSyncRun(forceIf = isCalled) {
        loadAndApplyPluginIfNotApplied(
            id = DEPS_GUARD_PLUGIN_ID,
            className = DEPS_GUARD_CLASS_NAME,
            version = DEPS_GUARD_PLUGIN_VERSION,
            catalogPluginId = DEPS_GUARD_PLUGIN_ALIAS,
        )

        rootProject.dependencyGuard {
            configuration("classpath")
        }

        // FIXME: configure non-root modules automatically
        // FIXME: configure allowedFilter
        // configuration("flavorReleaseCompileClasspath", RELEASE_CONFIGURATION)
        // configuration("flavorReleaseRuntimeClasspath", RELEASE_CONFIGURATION)

        // configuration("androidReleaseCompileClasspath", RELEASE_CONFIGURATION)
        // configuration("androidReleaseRuntimeClasspath", RELEASE_CONFIGURATION)
    }
}

internal fun Project.dependencyGuard(action: Action<in DependencyGuardPluginExtension>) {
    extensions.configure(DEPS_GUARD_EXTENSION_NAME, action)
}

private val RELEASE_CONFIGURATION: DependencyGuardConfiguration.() -> Unit = {
    allowedFilter = { dependency ->
        dependency.lowercase().let { d -> RELEASE_BLOCK_LIST.all { it !in d } }
    }
}

private val RELEASE_BLOCK_LIST = arrayOf(
    "junit", "test", "mock", "truth", "assert", "turbine", "robolectric"
)

/** @see DependencyGuardPlugin.DEPENDENCY_GUARD_EXTENSION_NAME */
private const val DEPS_GUARD_EXTENSION_NAME = "dependencyGuard"

/**
 * @see DependencyGuardPlugin.DEPENDENCY_GUARD_TASK_NAME
 * @see DependencyGuardPlugin.DEPENDENCY_GUARD_BASELINE_TASK_NAME
 */
private val DEPS_GUARD_TASK_NAMES = arrayOf(
    DEPS_GUARD_EXTENSION_NAME,
    DEPS_GUARD_EXTENSION_NAME + "Baseline",
    CHECK_TASK_NAME,
)

/** @see com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin */
private const val DEPS_GUARD_CLASS_NAME =
    "com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin"
