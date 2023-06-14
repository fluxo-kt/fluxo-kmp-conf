package fluxo.conf.feat

import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ID
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.impl.configureExtension
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// Plugin that guards against unintentional dependency changes
// https://github.com/dropbox/dependency-guard/blob/main/CHANGELOG.md#change-log
// https://mvnrepository.com/artifact/com.dropbox.dependency-guard/dependency-guard
internal fun FluxoKmpConfContext.prepareDependencyGuardPlugin() {
    val isCalled = hasAnyTaskCalled(DEPS_GUARD_TASK_NAMES)
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

        // FIXME: configure non-root modules automatically
        rootProject.configureExtension<DependencyGuardPluginExtension>(DEPS_GUARD_EXTENSION_NAME) {
            configuration("classpath")

            // FIXME: configure allowedFilter
        }
    }
}


/** @see DependencyGuardPlugin.DEPENDENCY_GUARD_EXTENSION_NAME */
private const val DEPS_GUARD_EXTENSION_NAME = "dependencyGuard"

/**
 * @see DependencyGuardPlugin.DEPENDENCY_GUARD_TASK_NAME
 * @see DependencyGuardPlugin.DEPENDENCY_GUARD_BASELINE_TASK_NAME
 */
private val DEPS_GUARD_TASK_NAMES = arrayOf(
    DEPS_GUARD_EXTENSION_NAME,
    "dependencyGuardBaseline",
    CHECK_TASK_NAME,
)

/** @see com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin */
private const val DEPS_GUARD_CLASS_NAME =
    "com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin"
