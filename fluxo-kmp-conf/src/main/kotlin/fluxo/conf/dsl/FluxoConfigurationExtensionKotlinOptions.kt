@file:Suppress("KDocUnresolvedReference")

package fluxo.conf.dsl

import fkcSetupGradlePlugin
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode

@FluxoKmpConfDsl
public interface FluxoConfigurationExtensionKotlinOptions : FluxoConfigurationExtensionCommon {

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
     * Use `-Xjdk-release` option to compile against the specified JDK API version,
     * similarly to javac's `-release`.
     *
     * Controls the target bytecode version and limits the API of the JDK in the
     * classpath to the specified Java version.
     *
     * Default value: `true`.
     * Inherited from the parent project if not set.
     * Used only for Kotlin _1.7.0_ or newer and JDK _9_ or newer.
     * And only for JVM non-Android builds.
     *
     * **WARN: This option isn't guaranteed to be effective for each JDK distribution!**
     * If there's no `ct.sym` file in JDK but `-Xjdk-release` is used,
     * the compiler will stop with an error.
     *
     * Links:
     * * [Kotlin 1.7: JDK Release Compatibility](https://blog.jetbrains.com/kotlin/2022/02/kotlin-1-7-jdk-release-compatibility/)
     * * [KT-29974](https://youtrack.jetbrains.com/issue/KT-29974)
     * * [Kotlin's JDK Release Compatibility Flag](https://jakewharton.com/kotlins-jdk-release-compatibility-flag/)
     * * [slack-gradle-plugin#778](https://github.com/slackhq/slack-gradle-plugin/pull/778/files)
     */
    public var useJdkRelease: Boolean

    /**
     * Flag to configure [Java toolchain](https://docs.gradle.org/current/userguide/toolchains.html)
     * both for Kotlin JVM and Java tasks.
     *
     * Turned off by default as it can slow down the build and usually suboptimal.
     * See [Gradle Toolchains are rarely a good idea](https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/)
     * for details.
     *
     * Inherited from the parent project if not set.
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension.jvmToolchain
     * @see org.gradle.jvm.toolchain.JavaToolchainSpec
     */
    public var setupJvmToolchain: Boolean


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
     * Inherited from the parent project if not set.
     *
     * **Default value: `false`.**
     *
     * @see setupCoroutines
     */
    public var optInInternal: Boolean?


    /**
     * Option that tells the Kotlin compiler
     * if and how to report issues on all public API declarations
     * without explicit visibility or return type.
     *
     * Inherited from the parent project if not set.
     * Default value:
     *  * `ExplicitApiMode.Strict` for Gradle plugins configured via [fkcSetupGradlePlugin]!
     *  * in other cases `null`.
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinTopLevelExtension.explicitApi
     */
    public var explicitApi: ExplicitApiMode?

    /**
     * Sets [explicitApi] option to report issues as errors.
     *
     * WARN: Automatically sets for Gradle plugins configured via [fkcSetupGradlePlugin]!
     */
    public fun explicitApi() {
        explicitApi = ExplicitApiMode.Strict
    }

    /**
     * Sets [explicitApi] option to report issues as warnings.
     */
    public fun explicitApiWarning() {
        explicitApi = ExplicitApiMode.Warning
    }


    /**
     * Flag to treat all warnings as errors.
     *
     * Inherited from the parent project if not set.
     *
     * **Default value: `false`.**
     *
     * @see org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions.allWarningsAsErrors
     */
    public var allWarningsAsErrors: Boolean?

    /**
     * Generate metadata for Java 1.8 reflection on method parameters.
     *
     * Inherited from the parent project if not set.
     *
     * **Default value: `false`.**
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
     * Inherited from the parent project if not set.
     *
     * **Default value: `true`.**
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
     * Inherited from the parent project if not set.
     * Default value: `true`.
     *
     * @see org.jetbrains.kotlin.gradle.plugin.LanguageSettingsBuilder.progressiveMode
     */
    public var progressiveMode: Boolean?

    /**
     * Flag to create an experimental compilation with the latest language features.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     */
    public var latestSettingsForTests: Boolean?

    /**
     * Flag to create an experimental compilation with the latest language features.
     *
     * Inherited from the parent project if not set.
     * Default value: `false`.
     */
    public var experimentalLatestCompilation: Boolean?

    /**
     * Flag to remove utility bytecode, eliminating names/data leaks in release artifacts
     * (better for minification and obfuscation).
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     *
     * Uses `-Xno-call-assertions`, `-Xno-param-assertions`, and `-Xno-receiver-assertions`
     * compiler options.
     *
     * [More info](https://proandroiddev.com/kotlin-cleaning-java-bytecode-before-release-9567d4c63911)
     * [2](https://www.guardsquare.com/blog/eliminating-data-leaks-caused-by-kotlin-assertions)
     */
    public var removeAssertionsInRelease: Boolean?

    /**
     * Flag to enable autoconfiguring JVM compatibility options.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     */
    public var setupJvmCompatibility: Boolean

    /**
     * Flag to enable autoconfiguring Kotlin options.
     *
     * Inherited from the parent project if not set.
     * Default value: `true`.
     */
    public var setupKotlinOptions: Boolean


    /**
     * Flag to turn on the new faster version of JAR FS should make build faster,
     * but it is experimental and causes warning.
     * So auto turned off when [allWarningsAsErrors] enabled.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     *
     * Uses `-Xuse-fast-jar-file-system` compiler option.
     */
    public var useExperimentalFastJarFs: Boolean?
}
