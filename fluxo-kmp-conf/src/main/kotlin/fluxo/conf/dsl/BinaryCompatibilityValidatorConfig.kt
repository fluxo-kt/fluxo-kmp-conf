package fluxo.conf.dsl

import isRelease
import org.gradle.api.Incubating

/**
 * Configuration for the [kotlinx.validation.BinaryCompatibilityValidatorPlugin].
 *
 * @see kotlinx.validation.ApiValidationExtension
 */
@Suppress("LongParameterList")
public class BinaryCompatibilityValidatorConfig(
    /**
     * Fully qualified package names that not consider public API.
     * For example, it could be `kotlinx.coroutines.internal`
     * or `kotlinx.serialization.implementation`.
     *
     * @see kotlinx.validation.ApiValidationExtension.ignoredPackages
     */
    public var ignoredPackages: MutableSet<String> = hashSetOf(),

    /**
     * Fully qualified names of annotations that effectively exclude declarations from being public.
     * Example of such annotation could be `kotlinx.coroutines.InternalCoroutinesApi`.
     *
     * [JvmSynthetic][kotlin.jvm.JvmSynthetic] is added by default.
     *
     * @see kotlinx.validation.ApiValidationExtension.nonPublicMarkers
     * @see JVM_SYNTHETIC_CLASS
     */
    public var nonPublicMarkers: MutableSet<String> = hashSetOf(JVM_SYNTHETIC_CLASS),

    /**
     * Fully qualified names of classes that ignored by the API check.
     * Example of such a class could be `com.package.android.BuildConfig`.
     *
     * [DefaultConstructorMarker][kotlin.jvm.internal.DefaultConstructorMarker]
     * is added by default.
     *
     * @see kotlinx.validation.ApiValidationExtension.ignoredClasses
     * @see DEFAULT_CONSTRUCTOR_MARKER_CLASS
     */
    public var ignoredClasses: MutableSet<String> =
        hashSetOf(DEFAULT_CONSTRUCTOR_MARKER_CLASS),

    /**
     * Whether to turn off validation for non-release builds.
     *
     * @see isRelease
     * @see kotlinx.validation.ApiValidationExtension.validationDisabled
     */
    public var disableForNonRelease: Boolean = false,


    /**
     * Whether to verify Kotlin JS/WASM TypeScript definitions APIs.
     *
     * See [fluxo-bcv-js](https://github.com/fluxo-kt/fluxo-bcv-js) for more info.
     */
    // https://github.com/fluxo-kt/fluxo-bcv-js
    public var tsApiChecks: Boolean = true,


    /**
     * Whether to verify KLib (Kotlin/Native) APIs.
     */
    // https://github.com/Kotlin/binary-compatibility-validator/issues/149#issuecomment-1768063785
    @Incubating
    public var klibValidationEnabled: Boolean = true,

    /**
     * When enabled, KLib validation will use v2-signatures in dumps.
     * It could be changed to `1` to use v1-signatures.
     *
     * If validated klib doesn't contain appropriate signatures,
     * an exception will be thrown during the validation.
     */
    @Incubating
    public var klibSignatureVersion: Int? = null,
)


/** @see kotlin.jvm.JvmSynthetic */
internal const val JVM_SYNTHETIC_CLASS = "kotlin.jvm.JvmSynthetic"

/**
 * Sealed classes constructors are not actually public.
 *
 * @see kotlin.jvm.internal.DefaultConstructorMarker
 */
internal const val DEFAULT_CONSTRUCTOR_MARKER_CLASS = "kotlin.jvm.internal.DefaultConstructorMarker"
