@file:JvmName("Fkc")
@file:JvmMultifileClass

import fluxo.conf.dsl.FluxoConfigurationExtension
import fluxo.conf.dsl.fluxoConfiguration
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

// TODO: Support JVM application with `application` plugin

/**
 * Lazily configures a Kotlin JVM module (Gradle [Project]).
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
 */
@JvmName("setupKotlin")
public fun Project.fkcSetupKotlin(
    setupKsp: Boolean? = null,
    optIns: List<String>? = null,
    kotlin: (KotlinJvmProjectExtension.() -> Unit)? = null,
    config: (FluxoConfigurationExtension.() -> Unit)? = null,
) {
    fluxoConfiguration {
        if (!optIns.isNullOrEmpty()) {
            this.optIns += optIns
        }

        setupKsp?.let { this.setupKsp = it }

        config?.invoke(this)

        asJvm {
            kotlin?.let { this.kotlin(action = it) }
        }
    }
}
