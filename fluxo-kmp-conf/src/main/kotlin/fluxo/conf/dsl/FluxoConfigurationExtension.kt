package fluxo.conf.dsl

import fluxo.conf.dsl.container.KmpConfigurationContainerDsl as KmpDsl
import fluxo.conf.impl.EMPTY_FUN

public interface FluxoConfigurationExtension :
    FluxoConfigurationExtensionKotlin,
    FluxoConfigurationExtensionAndroid,
    FluxoConfigurationExtensionPublication {

    public var skipDefaultConfigurations: Boolean


    /**
     * Configures the current project as a Kotlin Multiplatform module.
     */
    public fun configure(action: KmpDsl.() -> Unit = EMPTY_FUN)

    /** Alias for [configure] */
    public fun configureMultiplatform(action: KmpDsl.() -> Unit = EMPTY_FUN): Unit =
        configure(action)

    /** Alias for [configure] */
    public fun configuration(action: KmpDsl.() -> Unit = EMPTY_FUN): Unit = configure(action)


    /**
     * Declares default configuration for the current project and all subprojects.
     */
    public fun defaultConfiguration(action: KmpDsl.() -> Unit)


    /*
    setupMultiplatform
    setupAndroidLibrary
    setupAndroidApp
    setupKotlin
    setupIdeaPlugin

    setupBinaryCompatibilityValidator
    setupPublication
    setupVerification
    setupTestsReport
     */
}
