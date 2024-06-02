package fluxo.conf.dsl

import org.gradle.api.Incubating

@FluxoKmpConfDsl
public interface FluxoConfigurationExtensionCommon {

    /**
     * Flags project as an application.
     *
     * Enables some settings targeted for applications.
     * E.g. disables the binary compatibility validator.
     *
     * NOT inherited from the parent project if not set.
     * Default value: `false`.
     */
    public var isApplication: Boolean


    /**
     * Flag that allows to disable dependency setup completely.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
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


    // region Verification, Linting, and Formatting

    /**
     * Flag to turn on ALL the set of verification features, like Detekt, Spotless, and so on.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     *
     * @see enableSpotless
     * @see enableDetektCompilerPlugin
     */
    public var setupVerification: Boolean

    /**
     * Alias for [setupVerification]
     */
    public var enableVerification: Boolean
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
    @set:Incubating
    public var enableDetektCompilerPlugin: Boolean?

    /**
     * Flag to turn on the Detekt autocorrect feature.
     *
     * Disabled by default as can lead to compatibility errors
     * before every tool is compatible with the latest Kotlin updates.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     *
     * @see setupVerification
     */
    @set:Incubating
    public var enableDetektAutoCorrect: Boolean?


    /**
     * Flag to turn on the Android Lint setup for non-Android modules.
     * Can be disabled by the [setupVerification] flag.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     *
     * @see setupVerification
     */
    public var enableGenericAndroidLint: Boolean


    /**
     * Flag to turn on the Spotless setup.
     * Can be disabled by the [setupVerification] flag.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     *
     * @see setupVerification
     */
    public var enableSpotless: Boolean


    /**
     * Flag to apply the Gradle Doctor plugin.
     *
     * Only applicable for root module.
     * Default value: `true`.
     *
     * @see setupVerification
     */
    public var enableGradleDoctor: Boolean

    // endregion
}
