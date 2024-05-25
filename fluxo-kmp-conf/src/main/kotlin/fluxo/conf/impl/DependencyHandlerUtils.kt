@file:Suppress("TooManyFunctions")

package fluxo.conf.impl

import fluxo.log.e
import fluxo.log.l
import fluxo.log.w
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencyConstraint
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.dsl.DependencyConstraintHandler
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.plugin.HasKotlinDependencies
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler


private const val IMPLEMENTATION = org.jetbrains.kotlin.gradle.utils.IMPLEMENTATION
internal const val COMPILE_ONLY = org.jetbrains.kotlin.gradle.utils.COMPILE_ONLY


/**
 *
 * @see org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinDependencyHandler.kotlin
 */
@Suppress("UnusedReceiverParameter")
internal fun DependencyHandler.kotlin(module: String, version: String? = null): Any =
    "org.jetbrains.kotlin:kotlin-$module${version?.let { ":$version" }.orEmpty()}"


context(Project)
internal fun DependencyHandler.ksp(dependencyNotation: Any) =
    addAndLog("ksp", dependencyNotation)


context(Project)
internal fun DependencyHandler.implementation(dependencyNotation: Any) =
    addAndLog(IMPLEMENTATION, dependencyNotation)

context(Project)
internal fun DependencyHandler.implementation(
    dependencyNotation: Provider<*>,
    configuration: ExternalModuleDependency.() -> Unit,
) = addConfiguredDependencyTo(this, IMPLEMENTATION, dependencyNotation, configuration)

context(Project)
internal fun DependencyConstraintHandler.implementation(constraintNotation: Any) =
    addAndLog(IMPLEMENTATION, constraintNotation)

internal fun KotlinDependencyHandler.implementationAndLog(dependencyNotation: Any) =
    implementation(dependencyNotation).also {
        logKmpDependency(IMPLEMENTATION, it ?: dependencyNotation) {
            implementationConfigurationName
        }
    }

internal fun <T : Dependency> KotlinDependencyHandler.implementationAndLog(
    dependencyNotation: T,
    configure: T.() -> Unit,
) = implementation(dependencyNotation, configure).also {
    logKmpDependency(IMPLEMENTATION, it, " ($dependencyNotation)") {
        implementationConfigurationName
    }
}


context(Project)
internal fun DependencyHandler.testImplementation(dependencyNotation: Any) =
    addAndLog("testImplementation", dependencyNotation)


context(Project)
internal fun DependencyHandler.androidTestImplementation(dependencyNotation: Any): Dependency? =
    addAndLog("androidTestImplementation", dependencyNotation)

context(Project)
internal fun DependencyHandler.androidTestImplementation(
    dependencyNotation: Provider<*>,
    configuration: ExternalModuleDependency.() -> Unit,
) = addConfiguredDependencyTo(this, "androidTestImplementation", dependencyNotation, configuration)


context(Project)
internal fun DependencyHandler.debugImplementation(dependencyNotation: Any) =
    addAndLog("debugImplementation", dependencyNotation)

context(Project)
internal fun DependencyHandler.debugCompileOnly(dependencyNotation: Any) =
    addAndLog("debugCompileOnly", dependencyNotation)


context(Project)
internal fun DependencyHandler.runtimeOnly(dependencyNotation: Any) =
    addAndLog("runtimeOnly", dependencyNotation)


context(Project)
internal fun DependencyHandler.compileOnly(dependencyNotation: Any) =
    addAndLog(COMPILE_ONLY, dependencyNotation)

context(Project)
internal fun DependencyHandler.compileOnlyWithConstraint(dependencyNotation: Any) {
    compileOnly(dependencyNotation)
    constraints.implementation(dependencyNotation)
}

context(Project)
internal fun KotlinDependencyHandler.compileOnlyAndLog(dependencyNotation: Any) =
    compileOnly(dependencyNotation).also {
        logKmpDependency(COMPILE_ONLY, it ?: dependencyNotation) {
            compileOnlyConfigurationName
        }
    }


internal fun <T : ModuleDependency> T.exclude(group: String? = null, module: String? = null): T =
    uncheckedCast(exclude(excludeMapFor(group, module)))

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


private fun Project.addConfiguredDependencyTo(
    dependencies: DependencyHandler,
    configurationName: String,
    dependencyNotation: Provider<*>,
    action: ExternalModuleDependency.() -> Unit,
) {
    dependencies.addProvider(configurationName, dependencyNotation, action)
    logDependency(configurationName, dependencyNotation)
}


context(Project)
internal fun DependencyHandler.addAndLog(
    configurationName: String,
    dependencyNotation: Any,
) = add(configurationName, dependencyNotation).also {
    logDependency(configurationName, it ?: dependencyNotation)
}


context(Project)
private fun DependencyConstraintHandler.addAndLog(
    configurationName: String,
    dependencyNotation: Any,
): DependencyConstraint? {
    try {
        return add(configurationName, dependencyNotation).also {
            logDependency(configurationName, dependencyNotation, prefix = "constraint ")
        }
    } catch (e: Throwable) {
        logger.w("Failed to add $configurationName constraint for $dependencyNotation: $e", e)
        return null
    }
}

private inline fun KotlinDependencyHandler.logKmpDependency(
    configurationName: String,
    dependencyNotation: Any,
    extra: String = "",
    configurationNameAccessor: HasKotlinDependencies.() -> String,
) {
    val confName = try {
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        when (this) {
            is org.jetbrains.kotlin.gradle.plugin.mpp.DefaultKotlinDependencyHandler ->
                configurationNameAccessor(parent)

            else -> null
        }
    } catch (e: Throwable) {
        project.logger.e("Failed to get configuration name: $e", e)
        null
    } ?: "KMP/$configurationName"

    with(project) {
        logDependency(confName, dependencyNotation, extra)
    }
}

internal fun Project.logDependency(
    configurationName: String,
    dependencyNotation: Any,
    extra: String = "",
    prefix: String = "",
) {
    logger.l("$LOG_PREFIX$configurationName($prefix${dependencyNotation.dn})$extra")
}

private val Any.dn: String
    get() {
        return when (this) {
            is Dependency -> listOfNotNull(
                group,
                name,
                version,
            )

            is ModuleVersionSelector -> listOfNotNull(
                group,
                name,
                version,
            )

            is Provider<*> -> {
                return try {
                    @Suppress("RecursivePropertyAccessor")
                    this.orNull?.dn
                } catch (_: Throwable) {
                    null
                } ?: toString()
            }

            else -> return "'$this'"
        }.joinToString(":", prefix = "'", postfix = "'")
    }

private const val LOG_PREFIX = "D   --> "
