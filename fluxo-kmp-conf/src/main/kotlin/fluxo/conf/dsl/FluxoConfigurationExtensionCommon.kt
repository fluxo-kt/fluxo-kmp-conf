package fluxo.conf.dsl

public interface FluxoConfigurationExtensionCommon {

    /**
     * Flag that allows to disable dependency setup completely.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     */
    public var setupDependencies: Boolean

    /**
     * Flag to add the supported BOM dependencies automatically from the
     * toml version catalog on configuration of the project.
     *
     * Don't use it for libraries, only for the final app!
     *
     * Inherited from the parent project if not set. Default value: `false`.
     *
     * [More info](https://docs.gradle.org/current/userguide/java_platform_plugin.html)
     *
     * @TODO: Document list of supported BOMs
     */
    public var setupKnownBoms: Boolean


    /**
     * Flag to enable generation of the `BuildConfig` class for this module.
     * Uses native capability for Android-only modules, gradle plugin otherwise.
     *
     * Set default with `android.defaults.buildfeatures.buildconfig` property, false if not set.
     *
     * @see com.android.build.api.dsl.BuildFeatures.buildConfig
     * @FIXME: implement support for gradle plugin (support defaults property?)
     */
    public var enableBuildConfig: Boolean


    /**
     * Flag to turn on ALL the set of verification features, like Detekt, Spotless, and so on.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     *
     * @see enableSpotless
     * @see enableDetektCompilerPlugin
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


    /**
     * Flag to turn on the Detekt compiler plugin.
     * IT MAY BE HIGHLY UNSTABLE!
     *
     * Can be disabled by the [setupVerification] flag.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     *
     * @see setupVerification
     */
    public var enableDetektCompilerPlugin: Boolean?

    /**
     * Flag to turn on the Spotless setup.
     * Can be disabled by the [setupVerification] flag.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     *
     * @see setupVerification
     */
    public var enableSpotless: Boolean?
}
