package fluxo.conf.dsl

public interface FluxoConfigurationExtensionAndroid : FluxoConfigurationExtensionCommon {

    /**
     * Prefix for the auto generated [androidNamespace].
     *
     * Inherited from the parent project if not set.
     *
     * @see com.android.build.api.dsl.CommonExtension.namespace
     * @see com.android.build.gradle.BaseExtension.namespace
     */
    public var androidNamespacePrefix: String?

    /**
     * The namespace for the Android target.
     * Used for the generated `R` and `BuildConfig` classes.
     * Also, to resolve any relative class names in the `AndroidManifest.xml`.
     *
     * If not set, the default is the [androidNamespacePrefix] (if set) + the project path.
     *
     * @see com.android.build.api.dsl.CommonExtension.namespace
     * @see com.android.build.gradle.BaseExtension.namespace
     */
    public var androidNamespace: String


    /**
     * The minimum Android SDK version.
     * Use string value for preview versions, integer otherwise.
     *
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in toml version catalog:
     * `androidMinSdk`, `minSdk`, `androidMinSdkPreview`, `minSdkPreview` or `androidPreviewSdk`
     *
     * @see com.android.build.api.dsl.BaseFlavor.minSdk
     * @see com.android.build.api.dsl.BaseFlavor.minSdkPreview
     */
    public var androidMinSdk: Any

    /**
     * The target Android SDK version.
     * Use string value for preview versions, integer otherwise.
     *
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in toml version catalog:
     * `androidTargetSdk`, `targetSdk`, `androidTargetSdkPreview`, `targetSdkPreview`
     * or `androidPreviewSdk`
     *
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.targetSdk
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.targetSdkPreview
     * @see com.android.build.api.dsl.LibraryBaseFlavor.targetSdk
     * @see com.android.build.api.dsl.LibraryBaseFlavor.targetSdkPreview
     */
    public var androidTargetSdk: Any

    /**
     * The Android API level to compile your module against.
     * Use string value for preview versions, integer otherwise.
     *
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in toml version catalog:
     * `androidCompileSdk`, `compileSdk`, `androidCompileSdkPreview`, `compileSdkPreview`
     * or `androidPreviewSdk`
     *
     * @see com.android.build.api.dsl.CommonExtension.compileSdk
     * @see com.android.build.api.dsl.CommonExtension.compileSdkPreview
     * @see com.android.build.api.dsl.CommonExtension.compileSdkExtension
     */
    public var androidCompileSdk: Any


    /**
     * Specifies the version of the
     * [SDK Build Tools](https://developer.android.com/studio/releases/build-tools.html)
     * to use when building your project.
     *
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in the toml version catalog:
     * `androidBuildTools`, `buildToolsVersion`, `androidBuildToolsVersion`.
     *
     * @see com.android.build.api.dsl.CommonExtension.buildToolsVersion
     * @see com.android.build.gradle.BaseExtension.buildToolsVersion
     */
    public var androidBuildToolsVersion: String?

    /**
     * Specifies a list of
     * [alternative resources](https://d.android.com/guide/topics/resources/providing-resources.html#AlternativeResources)
     * (for example, languages) to keep in the final artifact.
     *
     * Inherited from the parent project if not set.
     *
     * @see com.android.build.api.dsl.BaseFlavor.resourceConfigurations
     */
    public var androidResourceConfigurations: Set<String>
}
