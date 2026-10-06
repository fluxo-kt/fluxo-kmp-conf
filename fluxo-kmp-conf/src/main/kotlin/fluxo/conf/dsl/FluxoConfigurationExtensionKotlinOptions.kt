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
     * When nothing sets it, the plugin derives it; run with `FLUXO_EXPLAIN=true` (environment
     * variable or Gradle property) to print the derived value and the reason for each module.
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
     * Limit the JDK API that Kotlin and Java sources can use to the JVM target, so code compiled
     * for 17 on a newer JDK cannot call newer JDK methods and then fail on a Java 17 runtime with
     * `NoSuchMethodError`. Kotlin gets `-Xjdk-release`, Java gets javac's `--release`.
     *
     * Decided per compile task from the JDK that task compiles with (its toolchain, else the JDK
     * running Gradle). It applies only when that JDK is newer than the target and ships
     * `lib/ct.sym`; otherwise nothing needs or can be limited. Skipped, with one warning naming
     * the cause:
     * * a compile JDK without `lib/ct.sym` (a trimmed or jlinked JDK); a `RELEASE=true` build
     *   fails instead;
     * * Kotlin targets 18-22 on a compile JDK below 23 (JDK-8331027, fixed in JDK 23); javac is
     *   still limited;
     * * javac in a module that passes `--add-exports`, `--add-reads` or `--patch-module`, which
     *   javac rejects together with `--release`.
     *
     * Android code gets no JVM-target limit, as its API is the device's, not the JDK's: Kotlin in
     * main (non-test) Android compilations stops seeing the JDK at all (`noJdk`, what KGP already
     * does for AGP 8's `kotlin-android`), so only `android.jar` is visible; Java is compiled by
     * AGP against the Android SDK. Lint's `NewApi` check covers calls above `minSdk`. Host tests
     * run on a JDK and keep its API.
     *
     * Default value: `true`. Inherited from the parent project if not set.
     * Run with `FLUXO_EXPLAIN=true` to print each task's decision and its reason.
     *
     * Links:
     * * [Kotlin 1.7: JDK Release Compatibility](https://blog.jetbrains.com/kotlin/2022/02/kotlin-1-7-jdk-release-compatibility/)
     * * [KT-29974](https://youtrack.jetbrains.com/issue/KT-29974)
     * * [JDK-8331027](https://bugs.openjdk.org/browse/JDK-8331027)
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
     * Applied only on CI (`CI=true`) or release (`RELEASE=true`) builds, and never to test,
     * JS, Wasm or shared-metadata compilations. Metadata compilations report an upstream
     * warning the code cannot fix (KT-69310), and every platform compilation recompiles the
     * same common sources under this flag.
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
     * Indy is the compiler's own default, so only `false` passes flags: `-Xlambdas=class` and
     * `-Xsam-conversions=class` (CI and release builds keep indy).
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
     * Whether the Kotlin/JVM compiler reads JARs with its fast JAR file system. The compiler
     * already uses it by default with the K2 frontend, so `true` passes nothing, and `false`
     * passes `-Xuse-fast-jar-file-system=false` to fall back to the slower implementation.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     */
    public var useExperimentalFastJarFs: Boolean?


    // Compiler defaults: each can be switched off here per module, or for the whole build with
    // `DISABLE_KOTLIN_DEFAULTS=<names>` (comma-separated, as the build log shows the flag, with or
    // without `-X`), which wins over these settings. An unknown name there fails the build.
    // `progressiveMode` (`progressive`) and `useJdkRelease` (`jdk-release`) take part too.

    /**
     * Treat JSR-305 nullability annotations in Java code (`@Nonnull`, `@Nullable`, Spring's,
     * …) as Kotlin nullability, so calling Java with a possibly null value is a compile error.
     * Passes `-Xjsr305=strict` on JVM compilations.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=jsr305`.
     */
    public var jsr305Strict: Boolean?

    /**
     * Run the JVM bytecode verifier on the generated class files, so a compiler bug producing
     * invalid bytecode fails the build instead of the program at runtime. Passes
     * `-Xvalidate-bytecode` on JVM compilations.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=validate-bytecode`.
     */
    public var validateBytecode: Boolean?

    /**
     * Write Kotlin type annotations (`TYPE_USE` targets) into JVM bytecode, so Java tools and
     * reflection see them. Passes `-Xemit-jvm-type-annotations` on JVM compilations.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=emit-jvm-type-annotations`.
     */
    public var emitJvmTypeAnnotations: Boolean?

    /**
     * Silence the warning the K2 compiler prints for every `@Suppress` of an error diagnostic
     * (KT-66513): the suppression is deliberate source code, the warning about it is noise that
     * warnings-as-errors would turn fatal. Passes `-Xdont-warn-on-error-suppression`.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=dont-warn-on-error-suppression`.
     */
    public var dontWarnOnErrorSuppression: Boolean?

    /**
     * Allow `expect`/`actual` classes in multiplatform modules without the compiler's Beta
     * warning on each of them. Passes `-Xexpect-actual-classes` on multiplatform compilations.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=expect-actual-classes`.
     */
    public var expectActualClasses: Boolean?

    /**
     * When the module's Kotlin language or API version is one its own Kotlin calls deprecated,
     * pass `-Xsuppress-version-warnings` so that warning alone doesn't fail a warnings-as-errors
     * build after a Kotlin upgrade; one build-end warning names the modules and the version to
     * move to instead. Off, the compiler's own warning stays (and fails such builds).
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=suppress-version-warnings`.
     */
    public var suppressVersionWarnings: Boolean?

    /**
     * Report an ignored result of a function whose result must be used (Kotlin's unused return
     * value checker), on Kotlin 2.3 and newer. Passes `-Xreturn-value-checker=check`.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=return-value-checker`.
     */
    public var returnValueChecker: Boolean?

    /**
     * Apply an annotation on a constructor `val`/`var` parameter to the property too, as Kotlin
     * does by default from language version 2.4, so a module behaves the same on every language
     * version. Passes `-Xannotation-default-target=param-property` below language version 2.4.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=annotation-default-target`.
     */
    public var annotationDefaultTargetParamProperty: Boolean?

    /**
     * Give a data class's generated `copy()` the visibility of its primary constructor, so a
     * private constructor can't be bypassed through `copy()` (KT-11914). In a library this can
     * change the public ABI: `@ConsistentCopyVisibility` or `@ExposedCopyVisibility` on the class
     * decides per class. Passes `-Xconsistent-data-class-copy-visibility`.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=consistent-data-class-copy-visibility`.
     */
    public var consistentDataClassCopyVisibility: Boolean?

    /**
     * Compile type-checking `when` expressions with `invokedynamic` on JVM targets 21 and newer.
     * Kotlin 2.4 already does; on Kotlin 2.2 and 2.3 this passes `-Xwhen-expressions=indy`.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=when-expressions`.
     */
    public var whenExpressionsIndy: Boolean?

    /**
     * Turn on the compiler's extra checks (`extraWarnings`, `-Wextra`): redundant or
     * suspicious code the default checks don't report.
     *
     * Inherited from the parent project if not set. Default value: `true`.
     * Off for the whole build: `DISABLE_KOTLIN_DEFAULTS=wextra`.
     */
    public var extraWarnings: Boolean?
}
