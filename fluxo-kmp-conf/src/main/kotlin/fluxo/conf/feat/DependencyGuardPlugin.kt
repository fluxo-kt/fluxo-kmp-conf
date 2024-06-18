@file:Suppress("MaxLineLength")

package fluxo.conf.feat

import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardConfiguration
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPlugin
import com.dropbox.gradle.plugins.dependencyguard.DependencyGuardPluginExtension
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_ID
import fluxo.conf.data.BuildConstants.DEPS_GUARD_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.log.d
import java.util.Locale
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME

// TODO: Opt-out configurable switch  via `fluxoKmpConf { depsGuard = false }`

// Plugin that guards against unintentional dependency changes
// https://github.com/dropbox/dependency-guard/blob/main/CHANGELOG.md#change-log
// https://mvnrepository.com/artifact/com.dropbox.dependency-guard/dependency-guard
internal fun FluxoKmpConfContext.prepareDependencyGuardPlugin() {
    val isCalled = hasStartTaskCalled(DEPS_GUARD_TASK_NAMES)
    if (isCalled) {
        markProjectInSync("$DEPS_GUARD_EXTENSION_NAME is called")
    }

    onProjectInSyncRun(forceIf = isCalled) {
        val root = rootProject
        loadAndApplyPluginIfNotApplied(project = root)

        // Nothing more is possible for the root project.
        // TODO: Update when resolved (https://github.com/dropbox/dependency-guard/issues/3)
        root.logger.d("Dependency guard the 'classpath' configuration")
        root.dependencyGuard {
            configuration("classpath", COMMON_CONFIGURATION)
        }

        // Configure non-root modules automatically
        root.subprojects p@{
            // TODO: Allow to customize projects auto-filtration
            //  (custom list, `modules` switch, `allowedFilter`, callback)

            // Skip test or benchmark modules
            val projectPath = path.lowercase(Locale.US)
            for (marker in TEST_MARKERS) {
                if (marker in projectPath) {
                    return@p
                }
            }

            val project = this
            loadAndApplyPluginIfNotApplied(project = project)

            // Guard all non-test, non-benchmark, non-meta configurations
            @Suppress("MaxLineLength")
            /** @see com.dropbox.gradle.plugins.dependencyguard.internal.ConfigurationValidators.validatePluginConfiguration */
            dependencyGuard {
                // TODO: Allow to customize configurations auto-filtration
                //  (custom list, `allowedFilter`, callback)

                project.configurations.configureEach {
                    if (isShouldBeGuarded()) {
                        val confName = name
                        logger.d("Dependency guard the '$confName' configuration")
                        this@dependencyGuard.configuration(confName, RELEASE_CONFIGURATION)
                    }
                }
            }
        }
    }
}

private fun Configuration.isShouldBeGuarded(): Boolean {
    return isCanBeResolved && name.lowercase(Locale.US).let { confNameL ->
        if (!isClasspathConfig(confNameL)) return@let false
        if (TEST_MARKERS.any { it in confNameL }) return@let false
        "release" in confNameL || NON_GUARD_MARKERS.none { it in confNameL }
    }
}

/** @see com.dropbox.gradle.plugins.dependencyguard.internal.ConfigurationValidators.isClasspathConfig */
private fun isClasspathConfig(configName: String): Boolean {
    return configName.endsWith("compileclasspath") ||
        configName.endsWith("runtimeclasspath")
}

private fun FluxoKmpConfContext.loadAndApplyPluginIfNotApplied(project: Project) =
    loadAndApplyPluginIfNotApplied(
        id = DEPS_GUARD_PLUGIN_ID,
        className = DEPS_GUARD_CLASS_NAME,
        version = DEPS_GUARD_PLUGIN_VERSION,
        catalogPluginId = DEPS_GUARD_PLUGIN_ALIAS,
        project = project,
    )

private fun Project.dependencyGuard(action: Action<in DependencyGuardPluginExtension>) =
    extensions.configure(DEPS_GUARD_EXTENSION_NAME, action)

private val COMMON_CONFIGURATION = Action<DependencyGuardConfiguration> {
    artifacts = true
    modules = true
}

private val RELEASE_CONFIGURATION = Action<DependencyGuardConfiguration> {
    COMMON_CONFIGURATION.execute(this)
    allowedFilter = { dependency ->
        dependency.lowercase(Locale.US).let { d ->
            RELEASE_BLOCK_LIST.all {
                it !in d || it == TEST && RELEASE_TEST_EXCLUDES.any { e -> e in d }
            }
        }
    }
}

private const val TEST = "test"

private val RELEASE_BLOCK_LIST = arrayOf(
    "assert",
    "junit",
    "mock",
    "robolectric",
    "truth",
    "turbine",
    TEST,
)

private val RELEASE_TEST_EXCLUDES = arrayOf(
    // Used in Detekt, explicitly allowed.
    // `io.github.davidburstrom.contester:contester-breakpoint`.
    "con$TEST",
    // `org.jetbrains.kotlinx:kotlinx-io-bytestring`.
    "by${TEST}ring",
)

private val TEST_MARKERS = arrayOf("test", "benchmark", "mock", "jmh")
private val NON_GUARD_MARKERS = TEST_MARKERS + arrayOf(
    "metadata", "scriptdef", "compilerplugin",
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
