package fluxo.conf.dsl

import isRelease

/**
 * Configuration for the [kotlinx.validation.BinaryCompatibilityValidatorPlugin].
 *
 * @see kotlinx.validation.ApiValidationExtension
 */
public class BinaryCompatibilityValidatorConfig(
    /**
     * Fully qualified package names that not consider public API.
     * For example, it could be `kotlinx.coroutines.internal`
     * or `kotlinx.serialization.implementation`.
     *
     * @see kotlinx.validation.ApiValidationExtension.ignoredPackages
     */
    public var ignoredPackages: MutableList<String> = mutableListOf(),

    /**
     * Fully qualified names of annotations that effectively exclude declarations from being public.
     * Example of such annotation could be `kotlinx.coroutines.InternalCoroutinesApi`.
     *
     * @see kotlinx.validation.ApiValidationExtension.nonPublicMarkers
     */
    public var nonPublicMarkers: MutableList<String> = mutableListOf(),

    /**
     * Fully qualified names of classes that ignored by the API check.
     * Example of such a class could be `com.package.android.BuildConfig`.
     *
     * @see kotlinx.validation.ApiValidationExtension.ignoredClasses
     */
    public var ignoredClasses: MutableList<String> = mutableListOf(),

    /**
     * Whether to turn off validation for non-release builds.
     *
     * @see isRelease
     * @see kotlinx.validation.ApiValidationExtension.validationDisabled
     */
    public var disableForNonRelease: Boolean = false,

    /**
     * Whether to verify JS API. Uses compiled TypeScript definitions.
     */
    public var jsApiChecks: Boolean = true,
)
