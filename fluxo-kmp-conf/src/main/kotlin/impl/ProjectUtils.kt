package impl

import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler

internal val Project.isRootProject: Boolean
    get() = rootProject == this

internal fun Project.checkIsRootProject(name: String) {
    require(isRootProject) { "$name MUST be called on a root project" }
}


internal inline fun Project.dependencies(configuration: DependencyHandler.() -> Unit) =
    dependencies.configuration()
