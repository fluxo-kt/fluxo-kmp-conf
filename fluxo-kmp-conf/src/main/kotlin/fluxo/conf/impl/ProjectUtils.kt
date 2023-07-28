package fluxo.conf.impl

import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.plugins.PluginAware


internal val CPUs = Runtime.getRuntime().availableProcessors()


internal val Project.isRootProject: Boolean
    get() = rootProject === this

internal fun Project.checkIsRootProject(name: String) {
    require(isRootProject) { "$name can only be used on a root project" }
}


internal inline fun Project.dependencies(configuration: DependencyHandler.() -> Unit) =
    dependencies.configuration()


internal fun PluginAware.withPlugin(id: String, action: Action<in AppliedPlugin>) {
    pluginManager.withPlugin(id, action)
}

internal fun PluginAware.withAnyPlugin(vararg ids: String, action: Action<in AppliedPlugin>) {
    ids.forEach { withPlugin(it, action) }
}
