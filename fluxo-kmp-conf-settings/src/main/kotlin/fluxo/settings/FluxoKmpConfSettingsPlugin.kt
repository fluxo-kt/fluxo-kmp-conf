package fluxo.settings

import fluxo.settings.data.BuildConstants
import java.util.concurrent.ConcurrentHashMap
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.initialization.Settings
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * The settings part of fluxo-kmp-conf: puts the plugins fluxo applies by id (KSP, plugin-publish,
 * dependency analysis) on each project's own build classpath before it is evaluated
 * ([addFluxoTools]). A separate artifact because settings plugins load into the settings class
 * loader, where the main plugin's classes couldn't see the Kotlin plugin.
 *
 * `gradle.lifecycle.beforeProject` is the Isolated-Projects-safe hook, so a project never reads
 * another project's state: each records its own class loader after evaluation, and its children
 * check those records for tools already on a parent's classpath.
 *
 * The root project gets the tools only in a single-project build. In a multi-project build a
 * tool on the root classpath makes every subproject that declares it with a version fail
 * ("already on the classpath with an unknown version"), and the root itself rarely needs one;
 * dependency analysis is the exception, as it reports at the root and is added only on request.
 */
public class FluxoKmpConfSettingsPlugin : Plugin<Settings> {
    override fun apply(settings: Settings) {
        val gradle = settings.gradle
        // Tells the root plugin not to register its own fallback hook.
        gradle.extensions.extraProperties.set(SETTINGS_PLUGIN_MARKER, BuildConstants.PLUGIN_VERSION)

        // Lifecycle actions are isolated (copied, sharing nothing), so they capture only plain
        // values; the class loaders projects share live in a build service, looked up inside
        // each action (a service provider is not isolatable).
        val allTools = fluxoTools(
            ksp = BuildConstants.KSP_PLUGIN_VERSION,
            pluginPublish = BuildConstants.GRADLE_PLUGIN_PUBLISH_PLUGIN_VERSION,
            dependencyAnalysis = BuildConstants.DEPS_ANALYSIS_PLUGIN_VERSION,
        )
        // `pluginManagement {}` is evaluated before any settings plugin is applied.
        val repositories = settings.pluginManagement.repositories
            .withType(MavenArtifactRepository::class.java).map { it.url }
        val tools = gradle.resolvableFluxoTools(
            allTools,
            repositories,
            probe = settings.buildscript,
            rootDir = settings.rootDir,
        )
        gradle.lifecycle.beforeProject {
            val project = this
            val projectTools = when {
                project.path != ":" || project.childProjects.isEmpty() -> tools
                else -> tools.filter { it.tasks.isNotEmpty() }
            }
            val state = project.toolState()
            project.addFluxoTools(projectTools, repositories) { id ->
                state.onAncestorClasspath(project.path, id)
            }
        }
        gradle.lifecycle.afterProject {
            toolState().loaders[path] = buildscript.classLoader
        }
    }
}

private fun Project.toolState() =
    gradle.sharedServices.registerIfAbsent("fluxoTools", ToolState::class.java) {}.get()

/** Each evaluated project's build class loader, shared by all projects. */
internal abstract class ToolState : BuildService<BuildServiceParameters.None> {
    val loaders = ConcurrentHashMap<String, ClassLoader>()

    /** Ancestors are evaluated first, so their class loaders are recorded by now. */
    fun onAncestorClasspath(path: String, pluginId: String): Boolean {
        val resource = "META-INF/gradle-plugins/$pluginId.properties"
        val ancestors = generateSequence(path) {
            if (it == ":") null else it.substringBeforeLast(':').ifEmpty { ":" }
        }.drop(1)
        return ancestors.any { loaders[it]?.getResource(resource) != null }
    }
}
