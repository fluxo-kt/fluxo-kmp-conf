package fluxo.settings

import java.io.File
import java.net.URI
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.verification.DependencyVerificationMode
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle

/**
 * A Gradle plugin fluxo applies by id that must already be on the module's own build classpath.
 * [tasks] non-empty: only needed when one of these tasks is requested.
 */
internal class FluxoTool(val id: String, val version: String, val tasks: Set<String> = emptySet())

/** Versions come from the caller's generated constants (the version catalog). */
internal fun fluxoTools(
    ksp: String,
    pluginPublish: String,
    dependencyAnalysis: String,
    bcvJs: String,
) = listOf(
    FluxoTool("com.google.devtools.ksp", ksp),
    FluxoTool("com.gradle.plugin-publish", pluginPublish),
    // TypeScript ABI checks (`tsApiChecks`); its classes reference the Kotlin plugin's JS DSL.
    FluxoTool(FLUXO_BCV_JS_PLUGIN_ID, bcvJs),
    FluxoTool(
        id = "com.autonomousapps.dependency-analysis",
        version = dependencyAnalysis,
        tasks = DEPENDENCY_ANALYSIS_TASKS,
    ),
)

internal const val FLUXO_BCV_JS_PLUGIN_ID = "io.github.fluxo-kt.binary-compatibility-validator-js"

/** Dependency analysis is only needed for its own reports, so only these requests add it. */
internal val DEPENDENCY_ANALYSIS_TASKS = setOf("buildHealth", "projectHealth", "reason")

/**
 * The [tools] this build may need: requested by task name where that applies, and resolvable
 * from [repositories], so an offline or blocked build behaves as before. With dependency
 * verification on, a failed probe would itself fail the build later, so tools are kept
 * unprobed: the consumer regenerates `verification-metadata.xml` once.
 *
 * Probed once per build through [probe], a script handler whose classpath is already resolved
 * (settings or root), never through a project's own: resolving marks repositories used, and a
 * consumer's later `content {}` filter on them would then fail.
 */
internal fun Gradle.resolvableFluxoTools(
    tools: List<FluxoTool>,
    repositories: List<URI>,
    probe: ScriptHandler,
    rootDir: File,
): List<FluxoTool> {
    val requested = startParameter.taskNames.map { it.substringAfterLast(':') }
    val needed = tools.filter { it.tasks.isEmpty() || requested.any(it.tasks::contains) }
    val verified = startParameter.dependencyVerificationMode != DependencyVerificationMode.OFF &&
        File(rootDir, "gradle/verification-metadata.xml").isFile
    if (needed.isEmpty() || verified) return needed
    probe.repositories.addMissing(repositories)
    return needed.filter { tool ->
        val dependency = probe.dependencies.create("${tool.marker}:${tool.version}")
        runCatching { probe.configurations.detachedConfiguration(dependency).resolve() }.isSuccess
    }
}

/**
 * Puts [tools] on [project]'s own build-script classpath; call it before the project is
 * evaluated.
 *
 * Why: KSP, fluxo-bcv-js, plugin-publish and dependency analysis need the Kotlin plugin's
 * classes (or apply
 * helper plugins by id), so fluxo can't load them into a class loader of its own, and Gradle
 * applies a plugin by id only from the project's build classpath. On that classpath fluxo then
 * applies them by id, and the configuration cache stores the build.
 *
 * Each is added with `prefer`, so a version the consumer declares for the module wins. Skipped:
 * a tool already on a parent's classpath ([onParentClasspath]; adding a second copy below it is
 * untested ground). [repositories] are the consumer's plugin repositories, so a mirror keeps
 * working.
 */
internal fun Project.addFluxoTools(
    tools: List<FluxoTool>,
    repositories: List<URI>,
    onParentClasspath: (pluginId: String) -> Boolean,
) {
    val needed = tools.filterNot { onParentClasspath(it.id) }
    if (needed.isEmpty()) return
    val handler = buildscript
    // A build script's classpath resolves only through its own repositories. A `plugins {}`
    // block that requests a plugin makes Gradle copy the settings plugin repositories into them
    // (an empty block doesn't), and those forbid any other build-script repository when they
    // use `exclusiveContent`. So ours go in next to the project's own, including ones its build
    // script declares or Gradle copies after this runs. A module with neither (no build file, or
    // no plugin requested) still has none when its classpath resolves; ours are then its only
    // source.
    val own = handler.repositories
    var added = false
    val addOnce = {
        if (!added) {
            added = true
            own.addMissing(repositories)
        }
    }
    if (own.isNotEmpty()) {
        addOnce()
    } else {
        own.whenObjectAdded { addOnce() }
        handler.configurations.named(ScriptHandler.CLASSPATH_CONFIGURATION) {
            withDependencies { if (own.isEmpty()) addOnce() }
        }
    }
    for (tool in needed) {
        val dependency = handler.dependencies.add("classpath", tool.marker)
        (dependency as ExternalModuleDependency).version { prefer(tool.version) }
    }
}

private val FluxoTool.marker get() = "$id:$id.gradle.plugin"

/** No [repositories]: the Plugin Portal, Gradle's default for plugins. */
private fun RepositoryHandler.addMissing(repositories: List<URI>) {
    if (repositories.isEmpty()) {
        gradlePluginPortal()
        return
    }
    val present = withType(MavenArtifactRepository::class.java).map { it.url.normalized() }
    for (uri in repositories) {
        if (uri.normalized() !in present) maven { url = uri }
    }
}

private fun URI.normalized() = toString().trimEnd('/')

/**
 * Set on `gradle.extensions` by the settings plugin (value: its version), so the root plugin
 * knows the tools are handled and registers no fallback hook of its own.
 */
internal const val SETTINGS_PLUGIN_MARKER = "fluxo.kmp.conf.settings"
