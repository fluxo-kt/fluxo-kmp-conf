package fluxo.conf.feat

import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants.DEPS_VERSIONS_PLUGIN_ALIAS
import fluxo.conf.data.BuildConstants.DEPS_VERSIONS_PLUGIN_ID
import fluxo.conf.data.BuildConstants.DEPS_VERSIONS_PLUGIN_VERSION
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.impl.withType

// Gradle Versions Plugin, provides a task to find, which dependencies have updates.
// https://github.com/ben-manes/gradle-versions-plugin/releases
// https://plugins.gradle.org/plugin/com.github.ben-manes.versions
internal fun FluxoKmpConfContext.prepareGradleVersionsPlugin() {
    // The plugin itself does register the task only on call.
    if (!hasStartTaskCalled(DEPS_VERSIONS_TASK_NAME)) {
        return
    }

    loadAndApplyPluginIfNotApplied(
        id = DEPS_VERSIONS_PLUGIN_ID,
        className = DEPS_VERSIONS_CLASS_NAME,
        version = DEPS_VERSIONS_PLUGIN_VERSION,
        catalogPluginId = DEPS_VERSIONS_PLUGIN_ALIAS,
    )

    rootProject.tasks.withType<DependencyUpdatesTask> {
        if (REJECT_CANDIDATE_RELEASES) {
            rejectVersionIf {
                !isNonStable(currentVersion) && isNonStable(candidate.version)
            }
        }
        checkForGradleUpdate = true
        checkConstraints = true
    }
}

private const val DEPS_VERSIONS_TASK_NAME = "dependencyUpdates"

/** @see com.github.benmanes.gradle.versions.VersionsPlugin */
private const val DEPS_VERSIONS_CLASS_NAME = "com.github.benmanes.gradle.versions.VersionsPlugin"


// Disallow release candidates as upgradable versions from stable versions
// https://github.com/ben-manes/gradle-versions-plugin#kotlin-dsl

private const val REJECT_CANDIDATE_RELEASES = true

private val STABLE_KEYWORDS = arrayOf("RELEASE", "FINAL", "GA")
private val STABLE_REGEX = "(?i)^[0-9a-zA-Z,.-]+$".toRegex()
private val UNSTABLE_REGEX = run {
    val unstableRegexParts = arrayOf(
        "alpha",
        "b(?:eta)?",
        "rc|cr|pr",
        "m(?:ilestone)?",
        "dev",
        "preview",
        "eap?",
    ).joinToString("|")

    @Suppress("RegExpUnnecessaryNonCapturingGroup")
    "(?i).*[.-](?:$unstableRegexParts)[.\\d+-]*".toRegex()
}

private fun isNonStable(version: String): Boolean {
    val hasStableKeyword = STABLE_KEYWORDS.any {
        version.contains(it, ignoreCase = true)
    }
    return !hasStableKeyword && (
        !STABLE_REGEX.matches(version) || UNSTABLE_REGEX.matches(
            version,
        )
        )
}
