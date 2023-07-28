package fluxo.conf.dsl

import fluxo.conf.dsl.container.KmpConfigurationContainerDsl as KmpDsl
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.impl.find
import org.gradle.api.Action
import org.gradle.api.Project

public interface FluxoConfigurationExtension :
    FluxoConfigurationExtensionKotlin,
    FluxoConfigurationExtensionAndroid,
    FluxoConfigurationExtensionPublication {

    /**
     * Ignore upper-level configuration, use only settings from the current module.
     */
    public var skipDefaultConfigurations: Boolean


    /**
     * Configures the current project as a Kotlin Multiplatform module.
     */
    public fun configureAsMultiplatform(action: KmpDsl.() -> Unit = EMPTY_FUN)

    /** Alias for [configureAsMultiplatform] */
    public fun configure(action: KmpDsl.() -> Unit = EMPTY_FUN): Unit = configure(action)

    /** Alias for [configureAsMultiplatform] */
    public fun configuration(action: KmpDsl.() -> Unit = EMPTY_FUN): Unit = configure(action)


    /**
     * Configures the current project as a Kotlin/JVM module.
     */
    public fun configureAsKotlinJvm(action: KmpDsl.() -> Unit = EMPTY_FUN)

    /**
     * Configures the current project as an IDEA plugin module.
     */
    public fun configureAsIdeaPlugin(action: KmpDsl.() -> Unit = EMPTY_FUN)

    /**
     * Configures the current project as a Gradle plugin module.
     */
    public fun configureAsGradlePlugin(action: KmpDsl.() -> Unit = EMPTY_FUN)

    /**
     * Configures the current project as an Android module.
     */
    public fun configureAsAndroid(app: Boolean = false, action: KmpDsl.() -> Unit = EMPTY_FUN)


    /**
     * Declares the default configuration for the current project and subprojects.
     */
    public fun defaultConfiguration(action: KmpDsl.() -> Unit)


    /*
    // TODO: Add remaining functionality from the old DSL

    setupBinaryCompatibilityValidator
    setupPublication
     */


    public companion object {
        public const val NAME: String = "fluxoConfiguration"
    }
}

internal fun Project.fluxoConfiguration(action: Action<in FluxoConfigurationExtension>) {
    extensions.configure(FluxoConfigurationExtension.NAME, action)
}

internal val Project.fluxoConfiguration: FluxoConfigurationExtensionImpl?
    get() = extensions.find(FluxoConfigurationExtension.NAME)


