package fluxo.conf.dsl

import fluxo.conf.dsl.container.target.KmpConfigurationContainerDsl
import fluxo.conf.impl.EMPTY_FUN

/**
 *
 * @TODO: use KotlinMultiplatformExtension.targetHierarchy?
 *   https://kotlinlang.org/docs/whatsnew1820.html#new-approach-to-source-set-hierarchy
 *
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension.targetHierarchy
 * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetHierarchyDsl.default
 */
public interface FluxoConfigurationExtension :
    FluxoConfigurationExtensionKotlin,
    FluxoConfigurationExtensionAndroid,
    FluxoConfigurationExtensionPublication {

    public var skipDefaultConfigurations: Boolean


    /**
     * Configures the current project as a Kotlin Multiplatform module.
     */
    public fun configure(action: KmpConfigurationContainerDsl.() -> Unit = EMPTY_FUN)

    /** Alias for [configure] */
    public fun configuration(action: KmpConfigurationContainerDsl.() -> Unit = EMPTY_FUN): Unit =
        configure(action)


    /**
     * Declares default configuration for the current project and all subprojects.
     */
    public fun defaultConfiguration(action: KmpConfigurationContainerDsl.() -> Unit)


    public fun setupMultiplatform() {}
}
