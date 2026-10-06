package fluxo.conf.deps

import fluxo.log.FluxoProblem
import fluxo.log.reportProblem
import javax.inject.Inject
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.configuration.BuildFeatures

/**
 * A plugin that works only from the project's own build classpath (it needs the Kotlin plugin's
 * classes, or applies helper plugins by id) is missing there: the settings plugin or the root
 * hook put it there for projects without child projects (`FluxoTool.kt`), so this is a parent
 * project (the root included) or a build that couldn't resolve it. Loaded by fluxo instead, it breaks the configuration cache,
 * so with the cache on the build stops here with the line that fixes it.
 */
internal fun missingFromBuildClasspath(project: Project, id: String, version: String?) {
    if (project.isConfigurationCacheActive()) {
        throw GradleException(missingFromBuildClasspathMessage(project, id, version))
    }
    project.reportMissingFromBuildClasspath(id, version)
}

/** Warns that plugin [id] must be declared; [consequence] says what happens until then. */
internal fun Project.reportMissingFromBuildClasspath(
    id: String,
    version: String?,
    consequence: String? = null,
) = reportProblem(
    FluxoProblem.PLUGIN_NOT_ON_BUILD_CLASSPATH,
    message = missingCause(this, id) + consequence?.let { " $it" }.orEmpty(),
    fix = missingFix(this, id, version),
)

internal fun missingFromBuildClasspathMessage(project: Project, id: String, version: String?) =
    missingCause(project, id) + " " + missingFix(project, id, version)

private fun missingCause(project: Project, id: String) =
    "'$id' is not on the build classpath of '${project.path}'."

private fun missingFix(project: Project, id: String, version: String?) =
    "Add `id(\"$id\")" + (if (version != null) " version \"$version\"" else "") +
        "` to the `plugins {}` block of " +
        (if (project.parent == null) "the root build script." else "'${project.path}'.")

internal fun Project.isConfigurationCacheActive(): Boolean =
    objects.newInstance(Features::class.java).buildFeatures.configurationCache.active.get()

// `BuildFeatures` is only injectable; `StartParameter`'s cache flag is deprecated.
internal open class Features @Inject constructor(val buildFeatures: BuildFeatures)
