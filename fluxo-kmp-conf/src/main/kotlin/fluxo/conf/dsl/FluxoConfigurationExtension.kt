package fluxo.conf.dsl

import fluxo.conf.dsl.container.KmpConfigurationContainerDsl
import fluxo.conf.impl.EMPTY_FUN

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


    /*
    setupMultiplatform
    setupAndroidLibrary
    setupAndroidApp
    setupKotlin
    setupBinaryCompatibilityValidator
    setupJsApp
    setupIdeaPlugin
    setupPublication
    setupVerification
    setupTestsReport
     */
}
