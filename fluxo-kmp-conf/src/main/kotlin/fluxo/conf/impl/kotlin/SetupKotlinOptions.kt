package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAll
import fluxo.log.e
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions

@Suppress("LongParameterList", "ComplexMethod", "LongMethod")
internal fun KotlinCommonCompilerOptions.setupKotlinOptions(
    conf: FluxoConfigurationExtensionImpl,
    compilationName: String,
    warningsAsErrors: Boolean,
    latestSettings: Boolean,
    isAndroid: Boolean,
    isTest: Boolean,
    isMultiplatform: Boolean,
    jvmTargetVersion: String?,
) {
    val context = conf.ctx
    val isCI = context.isCI
    val isRelease = context.isRelease
    val isReleaseTask = compilationName.contains("Release", ignoreCase = true)
    val releaseSettings = isCI || isRelease || isReleaseTask
    val useLatestSettings = !releaseSettings && latestSettings
    val kc = conf.kotlinConfig

    if (warningsAsErrors) {
        allWarningsAsErrors.set(true)
    }

    val compilerArgs = freeCompilerArgs.orElse(emptyList()).get().toMutableSet()
    compilerArgs.addAll(DEFAULT_OPTS)

    val (lang) = kc.langAndApiVersions(isTest = isTest, latestSettings = useLatestSettings)

    if (useLatestSettings) {
        compilerArgs.addAll(LATEST_OPTS)

        conf.explicitApi?.let {
            val v = when (it) {
                ExplicitApiMode.Strict -> "strict"
                ExplicitApiMode.Warning -> "warning"
                ExplicitApiMode.Disabled -> "disable"
            }
            compilerArgs.add("-Xexplicit-api=$v")
        }
    }

    // `-Xexpect-actual-classes` required for multiplatform projects since Kotlin 1.9.20;
    // unconditional under the layer-2 floor (consumer KGP 2.1.0+).
    if (isMultiplatform) {
        compilerArgs.add("-Xexpect-actual-classes")
    }

    val isJvm: Boolean
    val isJs: Boolean
    when (this) {
        is KotlinJvmCompilerOptions -> {
            isJvm = true
            isJs = false

            // FIXME: Move to JvmCompatibility?
            jvmTargetVersion?.let { jvmTarget ->
                setupJvmCompatibility(jvmTarget)

                val jvmTargetInt = jvmTarget.toJvmMajorVersion()
                // jdk-release applies in JVM non-Android builds when the JDK differs
                // from the target. The historical lang-2.0 + KGP-1.9.x escape arm is
                // unreachable under the layer-2 floor (KGP 2.0+); the prior
                // `!kotlin20orUpper` defensive disable was dropped together with that
                // floor bump.
                // https://github.com/slackhq/slack-gradle-plugin/commit/8445dbf943c6871a27a04186772efc1c42498cda
                val useJdkRelease = kc.useJdkRelease &&
                    !isAndroid &&
                    jvmTargetInt != JRE_VERSION

                // ct.sym is broken for -Xjdk-release=18+ with JDK 18..22.
                // https://bugs.openjdk.org/browse/JDK-8331027
                // https://youtrack.jetbrains.com/issue/KT-67668
                val jdkReleaseIsBroken = jvmTargetInt in (JRE_17 + 1) until JRE_23 &&
                    JRE_VERSION < JRE_23
                if (useJdkRelease && jdkReleaseIsBroken && !BROKEN_JDK_RELEASE_LOGGED) {
                    BROKEN_JDK_RELEASE_LOGGED = true
                    conf.project.logger.e(
                        "-Xjdk-release is broken for JRE 18..21 with JDK 18..22" +
                            ", so it is disabled! \n",
                        "https://bugs.openjdk.org/browse/JDK-8331027 \n",
                        "https://youtrack.jetbrains.com/issue/KT-67668",
                    )
                }

                // Compile against the specified JDK API version, similarly to javac's `-release`.
                if (useJdkRelease && !jdkReleaseIsBroken) {
                    compilerArgs.add("-Xjdk-release=$jvmTarget")

                    // TODO: Allow -Xjdk-release=1.6 with -jvm-target 1.8 for Kotlin 2.0+
                    //  https://youtrack.jetbrains.com/issue/KT-59098/Support-Xjdk-release1.6-with-jvm-target-1.8
                }
            }

            if (kc.javaParameters) {
                javaParameters.set(true)
            }

            compilerArgs.addAll(JVM_OPTS)
            if (useLatestSettings) {
                compilerArgs.addAll(LATEST_JVM_OPTS)
            }

            /** @see ANDROID_SAFE_JVM_TARGET */
            if (isAndroid && kc.useSafeAndroidOptions) {
                compilerArgs.add("-Xstring-concat=inline")
            }

            // Using the new faster version of JAR FS should make build faster,
            // but it is experimental and causes warning.
            if (!warningsAsErrors && kc.fastJarFs) {
                compilerArgs.add("-Xuse-fast-jar-file-system")
            }

            // "indy" mode generates lambda functions using `invokedynamic` instruction.
            // "class" mode provides lambdas arguments names and `reflect()` support.
            // `invokedynamic` instruction is supported since Java 8 and Android API 26.
            // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
            // https://kotlinlang.org/docs/whatsnew20.html#generation-of-lambda-functions-using-invokedynamic
            // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect.jvm/reflect.html
            val useIndyLambdas = kc.jvmTargetInt >= JRE_1_8 &&
                (kc.useIndyLambdas || isCI || releaseSettings)
            (if (useIndyLambdas) "indy" else "class").let { mode ->
                compilerArgs.addAll("-Xlambdas=$mode", "-Xsam-conversions=$mode")
            }

            if (releaseSettings && kc.removeAssertionsInRelease) {
                compilerArgs.addAll(JVM_RELEASE_OPTS)
            }
        }

        is KotlinJsCompilerOptions -> {
            isJs = true
            isJvm = false
            compilerArgs.addAll(JS_OPTS)
        }

        else -> {
            isJvm = false
            isJs = false
        }
    }

    if ((kc.progressive || useLatestSettings) && lang.isCurrentOrLater) {
        progressiveMode.set(true)
        // compilerArgs.add("-progressive")
    }

    if (useLatestSettings) {
        // K2 is the only compiler from Kotlin 2.0+; under the layer-2 floor (consumer
        // KGP 2.0+) it is the only path, and the `useK2` toggle is gone from KGP.

        // Lang features
        /** @see org.jetbrains.kotlin.config.LanguageFeature */
        // https://github.com/JetBrains/kotlin/blob/ca0b061/compiler/util/src/org/jetbrains/kotlin/config/LanguageVersionSettings.kt

        // Non-local break and continue are in preview since 2.0.
        // In K2 the feature is JVM-only.
        // https://youtrack.jetbrains.com/issue/KT-1436/Support-non-local-break-and-continue
        if (isJvm) {
            compilerArgs.add(langFeature("BreakContinueInInlineLambdas"))
        }

        // K2 Explicit backing fields (unconditional under the layer-2 floor).
        // https://github.com/Kotlin/KEEP/issues/278#issuecomment-1152073904
        // https://github.com/Kotlin/KEEP/pull/289
        compilerArgs.add(langFeature("ExplicitBackingFields"))

        // TODO: Guard conditions for when-with-subject (Kotlin 2.0.20)
        //  https://youtrack.jetbrains.com/issue/KT-67787
        // "-XXLanguage:+WhenGuards"
    }

    // OPT_IN_USAGE_ERROR is a warning in K2 (consumer KGP 2.0+, unconditional under
    // the layer-2 floor), preventing safe code gen compatible with `-Werror`.
    // https://youtrack.jetbrains.com/issue/KT-66513#focus=Comments-27-9461367.0-0
    if (warningsAsErrors) {
        compilerArgs.add("-Xdont-warn-on-error-suppression")
    }

    // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
    if (!releaseSettings && context.useKotlinDebug) {
        compilerArgs.add("-Xdebug")
    }

    freeCompilerArgs.set(compilerArgs.toList())
}

@Volatile
private var BROKEN_JDK_RELEASE_LOGGED = false

/** @see org.jetbrains.kotlin.config.LanguageFeature */
@Suppress("SameParameterValue")
private fun langFeature(name: String) = "-XXLanguage:+$name"


// https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
// https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/js/jsExtraHelp.out
// https://github.com/JetBrains/kotlin/blob/master/compiler/cli/cli-common/src/org/jetbrains/kotlin/cli/common/arguments/CommonCompilerArguments.kt
private val DEFAULT_OPTS: List<String> = listOf(

    // Experimental context receivers are deprecated and will be superseded by context parameters.
    // https://github.com/Kotlin/KEEP/blob/context-parameters/proposals/context-parameters.md.
    // "-Xcontext-receivers",
)

/** Latest options for early testing Kotlin compatibility or for non-production compilations. */
private val LATEST_OPTS = arrayOf(
    // Allow loading pre-release classes
    "-Xskip-prerelease-check",

    // Enable experimental value classes
    // https://youtrack.jetbrains.com/issue/KT-1179
    // https://github.com/Kotlin/KEEP/blob/master/notes/value-classes.md
    "-Xvalue-classes",

    // Compile using Front-end IR internal incremental compilation cycle.
    // Warning: this feature is far from being production-ready.
    "-Xuse-fir-ic",

    // Compile using LightTree parser with Front-end IR.
    "-Xuse-fir-lt",

    // Check pre- and postconditions on phases.
    "-Xcheck-phase-conditions",

    // Enable new experimental generic type inference algorithm.
    "-Xnew-inference",
).asList()

private val LATEST_JVM_OPTS = arrayOf(
    // Enhance not null annotated type parameter's types to definitely not null types
    // (@NotNull T → T & Any)
    "-Xenhance-type-parameter-types-to-def-not-null",

    // Allow using features from Java language that are in the preview phase.
    // Works as `--enable-preview` in Java.
    // All class files are marked as preview-generated, thus it won't be possible to use
    //  them in the release environment.
    "-Xjvm-enable-preview",
).asList()

// https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
private val JVM_OPTS = arrayOf(
    "-Xemit-jvm-type-annotations",
    "-Xjsr305=strict",
    "-Xjvm-default=all",
    "-Xtype-enhancement-improvements-strict-mode",
    "-Xvalidate-bytecode",

    // Not supported in the Kotlin 2.1 language version.
    // "-Xvalidate-ir",
).asList()

// Remove utility bytecode, eliminating names/data leaks in release obfuscated code.
// https://proandroiddev.com/kotlin-cleaning-java-bytecode-before-release-9567d4c63911
// https://www.guardsquare.com/blog/eliminating-data-leaks-caused-by-kotlin-assertions
private val JVM_RELEASE_OPTS = arrayOf(
    "-Xno-call-assertions",
    "-Xno-param-assertions",
    "-Xno-receiver-assertions",
).asList()

// https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/js/jsExtraHelp.out
private val JS_OPTS = arrayOf(
    // Perform extra optimizations on the generated JS code.
    "-Xoptimize-generated-js",
    // Generate JavaScript with ES2015 classes.
    "-Xes-classes",
).asList()

// TODO: -Xwasm-use-new-exception-proposal
//  https://kotlinlang.org/docs/whatsnew20.html#new-exception-handling-proposal-is-now-supported-under-the-option
