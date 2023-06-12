@file:Suppress("TooManyFunctions")

package fluxo.conf.impl

import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider

@Suppress("UnusedReceiverParameter")
internal fun DependencyHandler.kotlin(module: String, version: String? = null): Any =
    "org.jetbrains.kotlin:kotlin-$module${version?.let { ":$version" } ?: ""}"


internal fun DependencyHandler.ksp(dependencyNotation: Any) = add("ksp", dependencyNotation)


internal fun DependencyHandler.implementation(dependencyNotation: Any) =
    add("implementation", dependencyNotation)

internal fun DependencyHandler.implementation(
    dependencyNotation: Provider<*>,
    configuration: ExternalModuleDependency.() -> Unit,
) = addConfiguredDependencyTo(this, "implementation", dependencyNotation, configuration)


internal fun DependencyHandler.testImplementation(dependencyNotation: Any) =
    add("testImplementation", dependencyNotation)


internal fun DependencyHandler.androidTestImplementation(dependencyNotation: Any) =
    add("androidTestImplementation", dependencyNotation)

internal fun DependencyHandler.androidTestImplementation(
    dependencyNotation: Provider<*>,
    configuration: ExternalModuleDependency.() -> Unit,
) = addConfiguredDependencyTo(this, "androidTestImplementation", dependencyNotation, configuration)


internal fun DependencyHandler.debugImplementation(dependencyNotation: Any) =
    add("debugImplementation", dependencyNotation)

internal fun DependencyHandler.debugCompileOnly(dependencyNotation: Any) =
    add("debugCompileOnly", dependencyNotation)


internal fun DependencyHandler.runtimeOnly(dependencyNotation: Any) =
    add("runtimeOnly", dependencyNotation)


internal fun DependencyHandler.compileOnly(dependencyNotation: Any) =
    add("compileOnly", dependencyNotation)

internal fun DependencyHandler.compileOnlyWithConstraint(dependencyNotation: Any) {
    compileOnly(dependencyNotation)
    constraints.implementation(dependencyNotation)
}

internal fun DependencyConstraintHandler.implementation(constraintNotation: Any) =
    add("implementation", constraintNotation)


internal fun <T : ModuleDependency> T.exclude(group: String? = null, module: String? = null): T =
    uncheckedCast(exclude(excludeMapFor(group, module)))

@Suppress("UNCHECKED_CAST", "NOTHING_TO_INLINE")
private inline fun <T> uncheckedCast(obj: Any?): T = obj as T

private fun excludeMapFor(group: String?, module: String?): Map<String, String> =
    mapOfNonNullValuesOf(
        "group" to group,
        "module" to module,
    )

private fun mapOfNonNullValuesOf(vararg entries: Pair<String, String?>): Map<String, String> =
    mutableMapOf<String, String>().apply {
        for ((k, v) in entries) {
            if (v != null) {
                put(k, v)
            }
        }
    }


private fun addConfiguredDependencyTo(
    dependencies: DependencyHandler,
    configuration: String,
    dependencyNotation: Provider<*>,
    action: ExternalModuleDependency.() -> Unit,
) {
    dependencies.addProvider(configuration, dependencyNotation, action)
}
