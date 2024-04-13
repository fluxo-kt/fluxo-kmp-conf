@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/**
 * Lazily configures a Kotlin JVM app module (Gradle [Project]).
 * Suitable for Compose, CLI, or any other JVM application.
 *
 * @receiver The [Project] to configure.
 *
 * @param setupKsp Whether to set up KSP (auto-detected if already applied).
 * @param optIns List of the Kotlin opt-ins to add in the project.
 *
 * @param kotlin Configuration block for the [KotlinJvmProjectExtension].
 * @param config Configuration block for the [FluxoConfigurationExtension].
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.setupKsp
 * @see fluxo.conf.dsl.FluxoConfigurationExtension.optIns
 *
 * @see fkcSetupKotlinApp for a library configuration defaults.
 */
@JvmName("setupKotlinApp")
public fun Project.fkcSetupKotlinApp(
    setupKsp: Boolean? = null,
    optIns: List<String>? = null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
) {
    fluxoConfiguration {
        isApplication = true
        setupKotlin(optIns, setupKsp, config, kotlin)
    }
}
