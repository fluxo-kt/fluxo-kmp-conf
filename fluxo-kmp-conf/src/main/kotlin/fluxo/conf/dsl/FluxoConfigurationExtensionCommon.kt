package fluxo.conf.dsl

public interface FluxoConfigurationExtensionCommon {

    /**
     * Flag to enable generation of the `BuildConfig` class for this module.
     * Uses native capability for Android modules, gradle plugin otherwise.
     *
     * Set default with `android.defaults.buildfeatures.buildconfig` property, false if not set.
     *
     * @see com.android.build.api.dsl.BuildFeatures.buildConfig
     * @FIXME: implement support for gradle plugin (support defaults property?)
     */
    public var enableBuildConfig: Boolean


    /**
     * Flag to set up the KSP plugin.
     */
    public var setupKsp: Boolean?

    /**
     * Flag to set up the Kapt plugin.
     */
    public var setupKapt: Boolean?


    /**
     * Flag to enable the Compose feature.
     * Uses native capability for Android modules, multiplatform JetBrains Compose otherwise.
     *
     * @see com.android.build.api.dsl.BuildFeatures.compose
     */
    public var enableCompose: Boolean?

    /**
     * Turn on the `suppressKotlinVersionCompatibilityCheck` for Compose.
     * It prevents the Compose compiler from checking the Kotlin version.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     *
     * #### Compatibility maps:
     * - [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
     * - [Same for JetBrains Compose](https://github.com/JetBrains/compose-multiplatform/blob/master/VERSIONING.md#kotlin-compatibility)
     *
     * @see enableCompose
     */
    public var suppressKotlinComposeCompatibilityCheck: Boolean?


    /**
     * Flag to turn on the set of verification features, like Detekt, Spotless, and so on.
     */
    public var setupVerification: Boolean?

    /**
     * Alias for [setupVerification]
     */
    public var enableVerification: Boolean?
        get() = setupVerification
        set(value) {
            setupVerification = value
        }
}
