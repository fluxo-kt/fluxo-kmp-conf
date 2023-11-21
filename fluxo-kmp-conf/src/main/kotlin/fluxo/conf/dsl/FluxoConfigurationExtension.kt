package fluxo.conf.dsl

import fluxo.conf.dsl.container.KmpConfigurationContainerDsl as KmpDsl
import fluxo.conf.dsl.container.KotlinConfigurationContainerDsl as KstDsl
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.impl.find
import org.gradle.api.Action
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

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
    public fun asKmp(action: KmpDsl.() -> Unit = EMPTY_FUN)

    /** Alias for [asKmp] */
    public fun asMultiplatform(action: KmpDsl.() -> Unit = EMPTY_FUN): Unit = asKmp(action)

    /** Alias for [asKmp] */
    public fun configure(action: KmpDsl.() -> Unit = EMPTY_FUN): Unit = asKmp(action)


    /**
     * Configures the current project as a Kotlin/JVM module.
     */
    public fun asJvm(action: KstDsl<KotlinJvmProjectExtension>.() -> Unit = EMPTY_FUN)

    /**
     * Configures the current project as an IDEA plugin module.
     */
    public fun asIdeaPlugin(action: KstDsl<KotlinJvmProjectExtension>.() -> Unit = EMPTY_FUN)

    /**
     * Configures the current project as a Gradle plugin module.
     */
    public fun asGradlePlugin(action: KstDsl<KotlinJvmProjectExtension>.() -> Unit = EMPTY_FUN)

    /**
     * Configures the current project as an Android module.
     */
    public fun asAndroid(
        app: Boolean = false,
        action: KstDsl<KotlinAndroidProjectExtension>.() -> Unit = EMPTY_FUN,
    )


    /**
     * Declares the default configuration for the current project and subprojects.
     */
    public fun defaults(action: KmpDsl.() -> Unit)


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


