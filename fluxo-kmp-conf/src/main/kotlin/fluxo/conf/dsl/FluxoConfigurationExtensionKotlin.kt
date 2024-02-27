package fluxo.conf.dsl

import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

@FluxoKmpConfDsl
public interface FluxoConfigurationExtensionKotlin : FluxoConfigurationExtensionKotlinOptions {

    public var onConfiguration: (KotlinProjectExtension.() -> Unit)?

    /**
     * Lazy, skippable Gradle project configuration.
     * Only applied if the project is configured with at least one Kotlin target.
     */
    public fun onConfiguration(action: KotlinProjectExtension.() -> Unit) {
        onConfiguration = action
    }


    /**
     * Flag to add Kotlin `stdlib` dependency explicitly.
     *
     * Kotlin adds it automatically itself by default,
     * if not turned off bythe `kotlin.stdlib.default.dependency` gradle property.
     * But this property provides per-project control.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     *
     * [More info](https://kotlinlang.org/docs/gradle-configure-project.html#dependency-on-the-standard-library)
     */
    public var addStdlibDependency: Boolean

    /**
     * Flag to configure [Kotlin coroutines](https://github.com/Kotlin/kotlinx.coroutines)
     * dependencies and opt-ins.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     *
     * @see optInInternal
     */
    public var setupCoroutines: Boolean?

    /**
     * Set up basic [KotlinX serialization](https://github.com/Kotlin/kotlinx.serialization)
     * dependencies.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     */
    public var setupKotlinXSerialization: Boolean


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
     * Inherited from the parent project if not set.
     * Default value: `false`.
     *
     * #### Compatibility maps:
     * * [Compose to Kotlin Compatibility Map](https://developer.android.com/jetpack/androidx/releases/compose-kotlin)
     * * [Same for JetBrains Compose](https://github.com/JetBrains/compose-multiplatform/blob/master/VERSIONING.md#kotlin-compatibility)
     *
     * @see enableCompose
     */
    public var suppressKotlinComposeCompatibilityCheck: Boolean?


    /**
     * Flag to turn on the KotlinX BinaryCompatibilityValidator plugin.
     *
     * API dump is also used to generate R8/ProGuard keep rules!
     *
     * Default value: `true`.
     *
     * @see fluxo.minification.FluxoShrinkerConfig.autoGenerateKeepRulesFromApis
     */
    public var enableApiValidation: Boolean

    /**
     * Return the KotlinX BinaryCompatibilityValidator plugin configuration.
     * Switches on the [enableApiValidation] flag on access.
     *
     * @see enableApiValidation
     */
    public var apiValidation: BinaryCompatibilityValidatorConfig

    /**
     * Configure the KotlinX BinaryCompatibilityValidator plugin.
     * Switches on the [enableApiValidation] flag on call.
     *
     * @see enableApiValidation
     */
    public fun apiValidation(configure: BinaryCompatibilityValidatorConfig.() -> Unit = EMPTY_FUN) {
        apiValidation.apply(configure)
    }


    /**
     * Flag to use the KotlinX Dokka plugin as a documentation artifact generator.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     */
    public var useDokka: Boolean?


    // FIXME: koverReport settings
}
