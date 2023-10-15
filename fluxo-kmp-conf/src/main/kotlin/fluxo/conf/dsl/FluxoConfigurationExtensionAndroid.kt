package fluxo.conf.dsl

import com.android.build.api.variant.VariantBuilder

public interface FluxoConfigurationExtensionAndroid :
    FluxoConfigurationExtensionCommon,
    FluxoConfigurationExtensionPublication {

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
     * Additionally, it's used for the `applicationId` value.
     *
     * If not set, the default is the [androidNamespacePrefix] (if set) + the project name.
     * Or [group] if [androidNamespacePrefix] isn't set.
     *
     * @see com.android.build.api.dsl.CommonExtension.namespace
     * @see com.android.build.gradle.BaseExtension.namespace
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.applicationId
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.applicationIdSuffix
     * @see group
     */
    public var androidNamespace: String

    /**
     * The app ID.
     * Inherited from the parent project if not set.
     * [androidNamespace] is used as a fallback.
     *
     * See [Set the Application ID](https://developer.android.com/studio/build/application-id.html)
     *
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.applicationId
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.applicationIdSuffix
     * @see androidNamespace
     */
    public var androidApplicationId: String


    /**
     * Android app version code.
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in toml version catalog:
     * `versionCode` or `androidVersionCode`.
     *
     * Defaults to `0`.
     *
     * See [Versioning Your Application](http://developer.android.com/tools/publishing/versioning.html)
     *
     * @see com.android.build.api.dsl.ApplicationBaseFlavor.versionCode
     * @see version
     */
    public var androidVersionCode: Int


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


    /**
     * Disables Android build variants not matching the given [predicate].
     *
     * @see com.android.build.api.variant.AndroidComponentsExtension.beforeVariants
     * @see com.android.build.api.variant.VariantBuilder
     */
    public fun filterVariants(predicate: (VariantBuilder) -> Boolean)


    /**
     * List of Android build types for which no verification setup needed.
     */
    public var noVerificationBuildTypes: List<String>

    /**
     * List of Android build flavors for which no verification setup needed.
     */
    public var noVerificationFlavors: List<String>


    /**
     * Flag to set up Room for the module.
     *
     * Inherited from the parent project if not set.
     */
    public var setupRoom: Boolean?

    /**
     * Flag to remove kotlin metadata from the resulting APK or bundle.
     *
     * Inherited from the parent project if not set.
     */
    public var removeKotlinMetadata: Boolean
}
