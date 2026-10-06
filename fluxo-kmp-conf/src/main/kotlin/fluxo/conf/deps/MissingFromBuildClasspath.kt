package fluxo.conf.deps

import fluxo.log.w
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures

/**
 * A plugin that works only from the project's own build classpath (it needs the Kotlin plugin's
 * classes, or applies helper plugins by id) is missing there: the settings plugin or the root
 * hook put it there for subprojects (`FluxoTool.kt`), so this is the root project or a
 * build that couldn't resolve it. Loaded by fluxo instead, it breaks the configuration cache,
 * so with the cache on the build stops here with the line that fixes it.
 */
internal fun missingFromBuildClasspath(project: Project, id: String, version: String?) {
    val message = missingFromBuildClasspathMessage(project, id, version)
    if (project.isConfigurationCacheActive()) throw GradleException(message)
    project.logger.w(message)
}

internal fun missingFromBuildClasspathMessage(project: Project, id: String, version: String?) =
    "'$id' is not on the build classpath of '${project.path}'. Add `id(\"$id\")" +
        (if (version != null) " version \"$version\"" else "") + "` to the `plugins {}` block of " +
        (if (project.parent == null) "the root build script." else "'${project.path}'.")

internal fun Project.isConfigurationCacheActive(): Boolean =
    objects.newInstance(Features::class.java).buildFeatures.configurationCache.active.get()

// `BuildFeatures` is only injectable; `StartParameter`'s cache flag is deprecated.
internal open class Features @Inject constructor(val buildFeatures: BuildFeatures)
