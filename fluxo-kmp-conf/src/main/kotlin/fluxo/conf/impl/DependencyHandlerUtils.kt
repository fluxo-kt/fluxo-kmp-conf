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
import org.gradle.api.logging.Logger
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


internal fun Project.ksp(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "ksp", dependencyNotation)


internal fun Project.implementation(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, IMPLEMENTATION, dependencyNotation)

internal fun Project.implementation(
    dh: DependencyHandler,
    dependencyNotation: Provider<*>,
    configuration: ExternalModuleDependency.() -> Unit,
) = addConfiguredDependencyTo(dh, IMPLEMENTATION, dependencyNotation, configuration)

internal fun Project.implementation(dch: DependencyConstraintHandler, constraintNotation: Any) =
    addAndLog(dch, IMPLEMENTATION, constraintNotation)

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


internal fun Project.testImplementation(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "testImplementation", dependencyNotation)


internal fun Project.androidTestImplementation(
    dh: DependencyHandler,
    dependencyNotation: Any
): Dependency? = addAndLog(dh, "androidTestImplementation", dependencyNotation)

internal fun Project.androidTestImplementation(
    dh: DependencyHandler,
    dependencyNotation: Provider<*>,
    configuration: ExternalModuleDependency.() -> Unit,
) = addConfiguredDependencyTo(dh, "androidTestImplementation", dependencyNotation, configuration)


internal fun Project.debugImplementation(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "debugImplementation", dependencyNotation)

internal fun Project.debugCompileOnly(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "debugCompileOnly", dependencyNotation)


internal fun Project.runtimeOnly(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, "runtimeOnly", dependencyNotation)


internal fun Project.compileOnly(dh: DependencyHandler, dependencyNotation: Any) =
    addAndLog(dh, COMPILE_ONLY, dependencyNotation)

internal fun Project.compileOnlyWithConstraint(dh: DependencyHandler, dependencyNotation: Any) {
    compileOnly(dh, dependencyNotation)
    implementation(dh.constraints, dependencyNotation)
}

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


internal fun Project.addAndLog(
    dh: DependencyHandler,
    configurationName: String,
    dependencyNotation: Any,
) = dh.add(configurationName, dependencyNotation).also {
    logDependency(configurationName, it ?: dependencyNotation)
}


private fun Project.addAndLog(
    dch: DependencyConstraintHandler,
    configurationName: String,
    dependencyNotation: Any,
): DependencyConstraint? {
    try {
        return dch.add(configurationName, dependencyNotation).also {
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

    project.logDependency(confName, dependencyNotation, extra)
}

internal fun Project.logDependency(
    configurationName: String,
    dependencyNotation: Any,
    extra: String = "",
    prefix: String = "",
) {
    logger.logDependency(configurationName, dependencyNotation, extra, prefix)
}

internal fun Logger.logDependency(
    configurationName: String,
    dependencyNotation: Any,
    extra: String = "",
    prefix: String = "",
) {
    l("$LOG_PREFIX$configurationName($prefix${dependencyNotation.dn})$extra")
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
