package fluxo.conf.dsl

/**
 * Configuration for the [kotlinx.validation.BinaryCompatibilityValidatorPlugin].
 *
 * @see kotlinx.validation.ApiValidationExtension
 */
public data class BinaryCompatibilityValidatorConfig(
    /**
     * Fully qualified package names that not consider public API.
     * For example, it could be `kotlinx.coroutines.internal`
     * or `kotlinx.serialization.implementation`.
     *
     * @see kotlinx.validation.ApiValidationExtension.ignoredPackages
     */
    var ignoredPackages: List<String> = emptyList(),

    /**
     * Fully qualified names of annotations that effectively exclude declarations from being public.
     * Example of such annotation could be `kotlinx.coroutines.InternalCoroutinesApi`.
     *
     * @see kotlinx.validation.ApiValidationExtension.nonPublicMarkers
     */
    var nonPublicMarkers: List<String> = emptyList(),

    /**
     * Fully qualified names of classes that ignored by the API check.
     * Example of such a class could be `com.package.android.BuildConfig`.
     *
     * @see kotlinx.validation.ApiValidationExtension.ignoredClasses
     */
    var ignoredClasses: List<String> = emptyList(),

    /**
     * Whether to turn off validation for non-release builds.
     *
     * @see isRelease
     * @see kotlinx.validation.ApiValidationExtension.validationDisabled
     */
    var disableForNonRelease: Boolean = false,

    /**
     * Whether to verify JS API. Uses compiled TypeScript definitions.
     */
    var jsApiChecks: Boolean = true,
)
