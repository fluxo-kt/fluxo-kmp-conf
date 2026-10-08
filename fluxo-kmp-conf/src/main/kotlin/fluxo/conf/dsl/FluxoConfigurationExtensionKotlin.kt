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
     * Inherited from the parent project if not set. Default value: `true`.
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
     * Adds a second debug test binary and run (`<target>BackgroundTest`) to every Kotlin/Native
     * target with tests, compiled with `-trw`, so the tests run on a worker thread. It catches
     * code that only works on the main thread or relies on thread-local state; it roughly doubles
     * native link and test time, so it is off by default.
     *
     * The same is available in the Kotlin DSL: `kotlin { setupBackgroundNativeTests() }`.
     * To skip those runs in a build that has them, use `onlyIf { false }` on the tasks: the Kotlin
     * plugin sets `enabled` itself later, overriding `enabled = false`.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     */
    public var backgroundNativeTests: Boolean


    /**
     * Flag to set up the KSP plugin,
     * Auto-detected by the presence of the `kotlin-ksp` plugin.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     */
    public var setupKsp: Boolean?

    /**
     * Flag to set up the Kapt plugin.
     * Auto-detected by the presence of the `kotlin-kapt` or `com.android.legacy-kapt` plugin.
     *
     * Android modules on AGP 9's built-in Kotlin get `com.android.legacy-kapt` instead of
     * `kotlin-kapt`, which AGP rejects there. It isn't part of AGP's own dependencies, so declare
     * `id("com.android.legacy-kapt") version "<AGP version>" apply false` in the root
     * `plugins {}`; without it the build fails naming that line.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     */
    public var setupKapt: Boolean?


    // region Compose

    /**
     * Flag to enable the Compose feature.
     * Uses native capability for Android modules, multiplatform JetBrains Compose otherwise.
     * Supports the new Kotlin Compose compiler plugin.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     *
     * @see com.android.build.api.dsl.BuildFeatures.compose
     * @see org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension
     * @see org.jetbrains.compose.ComposeExtension
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

    // endregion


    // region BinaryCompatibilityValidator

    /**
     * Flag to turn on ABI (binary compatibility) validation: public API dumps in `api/`,
     * checked by `check`, updated by `apiDump`.
     *
     * On Kotlin 2.4+ it runs on the Kotlin Gradle plugin's own engine, with no extra plugin.
     * Below that, or when the build applies the KotlinX BinaryCompatibilityValidator (BCV)
     * plugin itself, or a BCV-only klib setting is used, it runs on BCV, which must then be
     * declared in the build. Both write the same dumps. `FLUXO_EXPLAIN=true` prints which one a
     * module uses.
     *
     * API dump is also used to generate R8/ProGuard keep rules!
     *
     * Default value: `false`. A benchmark module (kotlinx-benchmark or androidx.benchmark
     * applied) ignores the parent's value, as it has no API to keep: only its own setting counts.
     *
     * @see FluxoConfigurationExtensionPublication.autoGenerateKeepRulesFromApis
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

    // endregion


    /**
     * Flag to use the KotlinX Dokka plugin as a documentation artifact generator.
     *
     * WARN: Doesn't work well when Gradle configuration caching is enabled!
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     */
    public var useDokka: Boolean


    // FIXME: koverReport settings
}
