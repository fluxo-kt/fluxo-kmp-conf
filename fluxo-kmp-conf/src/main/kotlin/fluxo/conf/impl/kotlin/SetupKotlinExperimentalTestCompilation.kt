package fluxo.conf.impl.kotlin

import MAIN_SOURCE_SET_NAME
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.capitalizeAsciiOnly
import fluxo.gradle.addToCheckAndTestDependencies
import fluxo.log.e
import fluxo.log.l
import org.gradle.api.NamedDomainObjectContainer
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

internal val KotlinCompilation<*>.isExperimentalLatestCompilation: Boolean
    get() = name.let { it.startsWith(PREFIX) && it.endsWith(POSTFIX) }

@Suppress("ReturnCount")
internal fun KotlinTarget.setupExperimentalLatestCompilation(
    conf: FluxoConfigurationExtensionImpl,
    isMultiplatform: Boolean,
) {
    val kc = conf.kotlinConfig
    if (!kc.latestCompilation) {
        return
    }

    // Can't create custom metadata (common) compilations by name
    if (platformType == KotlinPlatformType.common) {
        return
    }

    // Doesn't work with KMP after Kotlin 1.9
    if (isMultiplatform && !kc.allowManualHierarchy) {
        // TODO: Find a solution for KMP 2.0
        return
    }

    try {
        val compilations = compilations
        val mainCompilation = compilations.getByName(MAIN_SOURCE_SET_NAME)
        mainCompilation.setupExperimentalLatestCompilation(
            compilations = compilations,
            isMultiplatform = isMultiplatform,
        )
    } catch (e: Throwable) {
        val logger = conf.project.logger
        logger.e("Failed to create experimental test compilation for $name target", e)
    }
}

/**
 * Creates experimental test compilation for checking code with the latest
 * and experimental features enabled.
 *
 * @receiver Main compilation to create experimental test compilation from.
 */
private fun KCompilation.setupExperimentalLatestCompilation(
    compilations: NamedDomainObjectContainer<out KCompilation>,
    isMultiplatform: Boolean,
) {
    val platform = if (isMultiplatform) platformType.name.capitalizeAsciiOnly() else ""
    val compilationName = "$PREFIX$platform$POSTFIX"
    val mainCompilation = this
    compilations.create(compilationName) {
        val project = mainCompilation.project
        project.logger.l(
            "'$name' compilation set up (experimental compilation with latest features)",
        )
        defaultSourceSet.dependsOn(mainCompilation.defaultSourceSet)
        project.addToCheckAndTestDependencies(compileTaskProvider, checkOnly = true)
    }
}

private const val PREFIX = "experimental"
private const val POSTFIX = "Latest"
