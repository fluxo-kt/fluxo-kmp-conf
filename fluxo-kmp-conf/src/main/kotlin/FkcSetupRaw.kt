@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Action
import org.gradle.api.Project

/**
 * Lazily configures a module (Gradle [Project]) with no presets.
 *
 * @receiver The [Project] to configure.
 *
 * @param config Configuration block for the [FluxoConfigurationExtension].
 */
@JvmName("setupRaw")
public fun Project.fkcSetupRaw(
    config: Action<in FluxoConfigurationExtension>,
) {
    fluxoConfiguration(config)
}
