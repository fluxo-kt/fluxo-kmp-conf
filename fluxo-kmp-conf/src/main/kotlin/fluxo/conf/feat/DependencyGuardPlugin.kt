package fluxo.conf.feat

import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardConfiguration
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ID
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import java.util.Locale
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

        val root = rootProject

        // Nothing more is possible for the root project.
        // TODO: Update when resolved (https://github.com/dropbox/dependency-guard/issues/3)
        root.dependencyGuard {
            configuration("classpath", COMMON_CONFIGURATION)
        }

        // Configure non-root modules automatically
        // TODO: Allow to customize auto-configuration
        //  (custom list, `modules` switch, `allowedFilter`)
        root.subprojects p@{
            // Skip test or benchmark modules
            val projectPath = path.lowercase(Locale.US)
            for (marker in TEST_MARKERS) {
                if (marker in projectPath) {
                    return@p
                }
            }

            // Guard all non-test, non-benchmark configurations
            dependencyGuard {
                project.configurations.configureEach {
                    val confName = name.lowercase(Locale.US)
                    val isRelease = "release" in confName ||
                        TEST_MARKERS.none { it in confName }
                    if (isRelease) {
                        this@dependencyGuard.configuration(confName, RELEASE_CONFIGURATION)
                    }
                }
            }
        }
    }
}

internal fun Project.dependencyGuard(action: Action<in DependencyGuardPluginExtension>) =
    extensions.configure(DEPS_GUARD_EXTENSION_NAME, action)

private val COMMON_CONFIGURATION = Action<DependencyGuardConfiguration> {
    artifacts = true
    modules = true
}

private val RELEASE_CONFIGURATION = Action<DependencyGuardConfiguration> {
    COMMON_CONFIGURATION.execute(this)
    allowedFilter = { dependency ->
        dependency.lowercase(Locale.US).let { d -> RELEASE_BLOCK_LIST.all { it !in d } }
    }
}

private val RELEASE_BLOCK_LIST = arrayOf(
    "junit", "test", "mock", "truth", "assert", "turbine", "robolectric",
)

private val TEST_MARKERS = arrayOf("test", "benchmark", "mock", "jmh")

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
