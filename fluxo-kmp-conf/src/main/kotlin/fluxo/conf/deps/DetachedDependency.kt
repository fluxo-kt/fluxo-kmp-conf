package fluxo.conf.deps

import fluxo.util.mapToArray
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

internal fun Project.detachedDependency(
    groupId: String,
    artifactId: String,
    version: String,
): Configuration = detachedDependency("$groupId:$artifactId:$version")

internal fun Project.detachedDependency(dependencyNotation: Any): Configuration =
    configurations.detachedConfiguration(
        dependencies.create(dependencyNotation),
    )

internal fun Project.detachedDependency(vararg dependencyNotation: Any): Configuration =
    detachedDependency(dependencyNotation)

@Suppress("SpreadOperator")
@JvmName("detachedDependencyArray")
internal fun Project.detachedDependency(dependencyNotations: Array<out Any>): Configuration =
    configurations.detachedConfiguration(
        *dependencyNotations.mapToArray(dependencies::create),
    )
