package fluxo.conf.dsl

import fluxo.conf.dsl.container.target.KmpConfigurationContainerDsl

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


    public fun configure(action: KmpConfigurationContainerDsl.() -> Unit)

    public fun defaultConfiguration(action: KmpConfigurationContainerDsl.() -> Unit)
}
