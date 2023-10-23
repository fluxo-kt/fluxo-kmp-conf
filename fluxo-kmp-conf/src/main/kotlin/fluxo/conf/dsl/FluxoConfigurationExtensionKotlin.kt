package fluxo.conf.dsl

public interface FluxoConfigurationExtensionKotlin : FluxoConfigurationExtensionCommon {

    /**
     * The Kotlin language version.
     * Provide source compatibility with the specified version of Kotlin.
     *
     * Possible values: '1.4 (deprecated)', '1.5 (deprecated)', '1.6', '1.7', '1.8', '1.9',
     * '2.0 (experimental)', '2.1 (experimental)'.
     * Set 'latest' or 'last' for the latest possible value.
     * Set 'current' for the current Kotlin plugin base value.
     *
     * Inherited from the parent project if not set.
     * Default value: `null`.
     *
     * Auto set using the version names in the toml version catalog:
     * `kotlinLangVersion`, `kotlinLang`.
     *
     * Note: can't be lower than [kotlinApiVersion]!
     *
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.languageVersion
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions.languageVersion
     * @see kotlinApiVersion
     * @see kotlinCoreLibraries
     * @see kotlinTestsLangVersion
     */
    public var kotlinLangVersion: String?

    /**
     * The Kotlin api version.
     * Allow using declarations only from the specified version of the bundled libraries.
     *
     * Possible values: '1.4 (deprecated)', '1.5 (deprecated)', '1.6', '1.7', '1.8', '1.9',
     * '2.0 (experimental)', '2.1 (experimental)'.
     * Set 'latest' or 'last' for the latest possible value.
     * Set 'current' for the current Kotlin plugin base value.
     *
     * Inherited from the parent project if not set.
     * Default value: [kotlinLangVersion].
     *
     * Auto set using the version names in the toml version catalog:
     * `kotlinApiVersion`, `kotlinApi`.
     *
     * Note: can't be greater than [kotlinLangVersion]!
     *
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.apiVersion
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions.apiVersion
     * @see kotlinLangVersion
     * @see kotlinCoreLibraries
     * @see kotlinTestsLangVersion
     */
    public var kotlinApiVersion: String?

    /**
     * Override the Kotlin language/api version for tests.
     * Provide source compatibility with the specified version of Kotlin.
     *
     * Possible values: '1.4 (deprecated)', '1.5 (deprecated)', '1.6', '1.7', '1.8', '1.9',
     * '2.0 (experimental)', '2.1 (experimental)'.
     * Set 'latest' or 'last' for the latest possible value.
     * Set 'current' for the current Kotlin plugin base value.
     *
     * Inherited from the parent project if not set.
     * Default value: `null`.
     *
     * Auto set using the version names in the toml version catalog:
     * `testsKotlinLangVersion`, `testsKotlinLang`.
     *
     * Note: can't be lower than [kotlinApiVersion] or [kotlinLangVersion]!
     *
     * @see kotlinApiVersion
     * @see kotlinLangVersion
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.languageVersion
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions.languageVersion
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.apiVersion
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions.apiVersion
     */
    public var kotlinTestsLangVersion: String?

    /**
     * Version of the core Kotlin libraries added to Kotlin compile classpath,
     * unless stdlib dependency already added to the project.
     *
     * Inherited from the parent project if not set.
     * By default, this version is the same as the version of the used Kotlin Gradle plugin.
     *
     * Auto set using the version names in the toml version catalog:
     * `kotlinCoreLibraries`, `kotlinCoreLibrariesVersion`, `kotlinStdlib`,
     * `kotlin`, `kotlinVersion`.
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension.coreLibrariesVersion
     * @see kotlinLangVersion
     * @see kotlinApiVersion
     */
    public var kotlinCoreLibraries: String?


    /**
     * The Java lang target (and source) version.
     * Configures to generate class files suitable for the specified Java SE release.
     * And compiles source code according to the rules of the Java programming language
     * for the specified Java SE release.
     *
     * Set 'latest' or 'last' for the latest possible value.
     * Set 'current' for the current Kotlin plugin base value.
     *
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in the toml version catalog:
     * `jvmTarget`, `javaLangTarget`, `javaLangSource`, `javaToolchain`,
     * `sourceCompatibility`, `targetCompatibility`.
     *
     * Note: the Java lang target must not be lower than the source release.
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions.jvmTarget
     * @see org.gradle.api.plugins.JavaPluginExtension.setSourceCompatibility
     * @see org.gradle.api.plugins.JavaPluginExtension.setTargetCompatibility
     * @see org.gradle.api.tasks.compile.AbstractCompile.setSourceCompatibility
     * @see org.gradle.api.tasks.compile.AbstractCompile.setTargetCompatibility
     * @see org.gradle.jvm.toolchain.JavaToolchainSpec.getLanguageVersion
     * @see javaTestsLangTarget
     */
    public var javaLangTarget: String?

    /**
     * Override the [Java lang target (and source) version][javaLangTarget] for tests.
     *
     * Inherited from the parent project if not set.
     *
     * Auto set using the version names in the toml version catalog:
     * `jvmTestsTarget`, `javaTestsLangTarget`
     *
     * Note: can't be lower than [javaLangTarget]!
     *
     * @see javaLangTarget
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions.jvmTarget
     * @see org.gradle.api.plugins.JavaPluginExtension.setSourceCompatibility
     * @see org.gradle.api.plugins.JavaPluginExtension.setTargetCompatibility
     * @see org.gradle.api.tasks.compile.AbstractCompile.setSourceCompatibility
     * @see org.gradle.api.tasks.compile.AbstractCompile.setTargetCompatibility
     * @see org.gradle.jvm.toolchain.JavaToolchainSpec.getLanguageVersion
     */
    public var javaTestsLangTarget: String?

    /** Alias for [javaLangTarget] */
    public var jvmTarget: String?
        get() = javaLangTarget
        set(value) {
            javaLangTarget = value
        }

    /**
     * Flag to configure [Java toolchain](https://docs.gradle.org/current/userguide/toolchains.html)
     * both for Kotlin JVM and Java tasks.
     *
     * Turned off by default as it can slow down the build.
     *
     * Inherited from the parent project if not set.
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension.jvmToolchain
     * @see org.gradle.jvm.toolchain.JavaToolchainSpec
     */
    public var setupJvmToolchain: Boolean?


    /**
     * Flag that allows to disable kotlin plugin configuration completely.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     */
    public var setupKotlin: Boolean


    /**
     * List of Kotlin opt-ins to add in the project.
     *
     * Default set of opt-ins:
     * - [kotlin.RequiresOptIn]
     * - [kotlin.contracts.ExperimentalContracts]
     * - [kotlin.experimental.ExperimentalObjCName]
     * - [kotlin.experimental.ExperimentalTypeInference]
     *
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.optIn
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerToolOptions.freeCompilerArgs
     */
    public var optIns: List<String>

    /**
     * Flag to add opt-ins for internal Kotlin and Coroutines features.
     *
     * For Coroutines:
     * - [kotlinx.coroutines.DelicateCoroutinesApi]
     * - [kotlinx.coroutines.ExperimentalCoroutinesApi]
     * - [kotlinx.coroutines.InternalCoroutinesApi]
     *
     * @see setupCoroutines
     */
    public var optInInternal: Boolean?


    /**
     * Flag to report an error for any Kotlin warning.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerToolOptions.allWarningsAsErrors
     * @TODO: set for Java compilation tasks too (https://stackoverflow.com/q/30806920)
     */
    public var warningsAsErrors: Boolean?

    /**
     * Generate metadata for Java 1.8 reflection on method parameters.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     *
     * [More details](https://docs.oracle.com/javase/tutorial/reflect/member/methodparameterreflection.html)
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions.javaParameters
     * @TODO: set for Java compilation tasks too (https://stackoverflow.com/q/37463902)
     */
    public var javaParameters: Boolean?

    /**
     * Flag to turn off dynamic invocations (`invokedynamic`) compilation for Kotlin lambdas
     * and SAM conversions (`indy` mode).
     *
     * Inherited from the parent project if not set. Default value: `true`.
     *
     * Indy mode produces faster and more compact bytecode,
     * using the `invokedynamic` JVM instruction.
     * Note: legacy `class` mode provides names for lambda arguments. Indy mode doesn't!
     *
     * Uses `-Xlambdas` and `-Xsam-conversions` compiler options.
     *
     * [More info](https://kotlinlang.org/docs/whatsnew15.html#lambdas-via-invokedynamic)
     */
    public var useIndyLambdas: Boolean?

    /**
     * Flag to turn on the progressive mode.
     *
     * Deprecations and bug fixes for unstable code take effect immediately in this mode.
     * Instead of going through a graceful migration cycle.
     *
     * Progressive code is backward compatible. But not otherwise.
     *
     * Only applied if the latest [kotlinLangVersion] used (otherwise meaningless).
     *
     * Inherited from the parent project if not set. Default value: `true`.
     *
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.progressiveMode
     */
    public var progressiveMode: Boolean?

    /**
     * Flag to create an experimental compilation with the latest language features.
     */
    public var latestSettingsForTests: Boolean?

    /**
     * Flag to create an experimental compilation with the latest language features.
     */
    public var experimentalLatestCompilation: Boolean?

    /**
     * Flag to remove utility bytecode, eliminating names/data leaks in release artifacts
     * (better for minification and obfuscation).
     *
     * Inherited from the parent project if not set. Default value: `true`.
     *
     * Uses `-Xno-call-assertions`, `-Xno-param-assertions`, and `-Xno-receiver-assertions`
     * compiler options.
     *
     * [More info](https://proandroiddev.com/kotlin-cleaning-java-bytecode-before-release-9567d4c63911)
     * [2](https://www.guardsquare.com/blog/eliminating-data-leaks-caused-by-kotlin-assertions)
     */
    public var removeAssertionsInRelease: Boolean?


    /**
     * Flag that allows to disable dependency setup completely.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     */
    public var setupDependencies: Boolean

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
     * Flag to turn on the new faster version of JAR FS should make build faster,
     * but it is experimental and causes warning.
     * So auto turned off when [warningsAsErrors] enabled.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     *
     * Uses `-Xuse-fast-jar-file-system` compiler option.
     */
    public var useExperimentalFastJarFs: Boolean?


    /**
     * Flag to turn on the KotlinX BinaryCompatibilityValidator plugin
     */
    public var enableApiValidation: Boolean?

    public var apiValidation: BinaryCompatibilityValidatorConfig?

    public fun apiValidation(configure: BinaryCompatibilityValidatorConfig.() -> Unit) {
        apiValidation = BinaryCompatibilityValidatorConfig().apply(configure)
    }


    /**
     * Flag to turn on the Detekt compiler plugin.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     */
    public var enableDetektCompilerPlugin: Boolean?

    /**
     * Flag to turn on the Spotless setup.
     * Can be disabled by the [setupVerification] flag.
     *
     * Inherited from the parent project if not set. Default value: `false`.
     */
    public var enableSpotless: Boolean?


    // FIXME: koverReport settings
    // FIXME: Dokka settings
}
