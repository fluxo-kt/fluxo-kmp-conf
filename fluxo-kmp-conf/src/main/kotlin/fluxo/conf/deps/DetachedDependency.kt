package fluxo.conf.deps

import fluxo.util.mapToArray
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration

@Suppress("SpreadOperator")
internal fun Project.detachedDependency(dependencyNotations: Array<out Any>): Configuration =
    configurations.detachedConfiguration(
        *dependencyNotations.mapToArray(dependencies::create),
    )
