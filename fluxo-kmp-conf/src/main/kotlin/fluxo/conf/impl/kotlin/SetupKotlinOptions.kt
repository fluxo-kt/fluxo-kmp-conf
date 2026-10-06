package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAll
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
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
    isWasm: Boolean,
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
    optIn.addAll(if (isTest) kc.prepareTestOptIns() else kc.optIns)

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

    if (isMultiplatform) {
        compilerArgs.addDefault(KotlinDefault.EXPECT_ACTUAL_CLASSES)
    }

    // Read from the compile task's own options, after ours were applied, so a version the
    // consumer set in their own `kotlin { compilerOptions }` counts too.
    val deprecatedVersions = listOfNotNull(languageVersion.orNull, apiVersion.orNull)
        .distinct().filter { it.isDeprecatedByKgp }
    if (deprecatedVersions.isNotEmpty()) {
        compilerArgs.addDefault(KotlinDefault.SUPPRESS_VERSION_WARNINGS)
        val path = conf.project.path
        deprecatedVersions.forEach { context.deprecatedKotlinVersions.record(it, path) }
    }

    when (this) {
        is KotlinJvmCompilerOptions -> {
            if (jvmTargetVersion == null && isAndroid) {
                followAndroidJavaTarget(conf.project)
            }
            // The JDK API limit (-Xjdk-release) is added per task by `limitKotlinJdkApi`.
            jvmTargetVersion?.let { setupJvmCompatibility(it) }

            if (kc.javaParameters) {
                javaParameters.set(true)
            }
            // The typed `jvmDefault` option exists only since KGP 2.2; the plugin is APPLIED
            // against the consumer's KGP and the layer-2 floor is Kotlin 2.1, where calling the
            // 2.2 getter throws NoSuchMethodError. `-Xjvm-default=all` is the 2.1 equivalent of
            // `NO_COMPATIBILITY` but is deprecated on 2.2+ (would trip `-Werror`), so gate by
            // the consumer's KGP version. The 2.2-only symbol is reached only on 2.2+.
            if (context.kotlinPluginVersion >= KOTLIN_2_2) {
                jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
            } else {
                compilerArgs.add("-Xjvm-default=all")
            }

            compilerArgs.addDefault(KotlinDefault.EMIT_JVM_TYPE_ANNOTATIONS)
            compilerArgs.addDefault(KotlinDefault.JSR305)
            compilerArgs.addDefault(KotlinDefault.VALIDATE_BYTECODE)
            if (useLatestSettings) {
                compilerArgs.addAll(LATEST_JVM_OPTS)
            }

            /** @see ANDROID_SAFE_JVM_TARGET */
            if (isAndroid && kc.useSafeAndroidOptions) {
                compilerArgs.add("-Xstring-concat=inline")
            }

            // The compiler uses the fast JAR file system by default with K2 (`?: useK2` in
            // KotlinCoreEnvironment, 2.1.21 to 2.4.20), the only frontend on the supported range,
            // so only switching it off needs a flag.
            if (!kc.fastJarFs) {
                compilerArgs.add("-Xuse-fast-jar-file-system=false")
            }

            // "indy" mode generates lambda functions using `invokedynamic` instruction.
            // "class" mode provides lambdas arguments names and `reflect()` support.
            // `indy` is the compiler's default from language version 2.0, so only `class` is
            // passed.
            // https://kotlinlang.org/docs/whatsnew20.html#generation-of-lambda-functions-using-invokedynamic
            // https://kotlinlang.org/api/latest/jvm/stdlib/kotlin.reflect.jvm/reflect.html
            val useIndyLambdas = kc.jvmTargetInt >= JRE_1_8 &&
                (kc.useIndyLambdas || isCI || releaseSettings)
            if (!useIndyLambdas) {
                compilerArgs.addAll("-Xlambdas=class", "-Xsam-conversions=class")
            }

            if (releaseSettings && kc.removeAssertionsInRelease) {
                compilerArgs.addAll(JVM_RELEASE_OPTS)
            }
        }

        is KotlinJsCompilerOptions -> {
            // ES2015 classes under the Kotlin plugin's default ES5 target. JS only: Wasm shares
            // these options, but Kotlin 2.4 compiles it with its own argument set, where a
            // JS-only flag warns "not supported by this version of the compiler".
            // `-Xoptimize-generated-js` is on by default on the whole supported range.
            if (!isWasm) {
                useEsClasses.set(true)
            }
        }
    }

    if ((kc.progressive || useLatestSettings) && lang.isCurrentOrLater) {
        progressiveMode.set(true)
        // compilerArgs.add("-progressive")
    }

    if (useLatestSettings) {
        // K2 is the only compiler from Kotlin 2.0+; under the layer-2 floor (consumer
        // KGP 2.0+) it is the only path, and the `useK2` toggle is gone from KGP.

        // Lang features. This compilation uses the newest language version the consumer's Kotlin
        // knows (2.3 on Kotlin 2.1), so only features that are not stable there need a flag:
        // enabling a stable one is a "redundant argument" warning.
        /** @see org.jetbrains.kotlin.config.LanguageFeature */

        // Explicit backing fields: stable from Kotlin 2.4; the official flag exists since 2.3.
        // https://github.com/Kotlin/KEEP/issues/278#issuecomment-1152073904
        val kgp = context.kotlinPluginVersion
        when {
            kgp >= KOTLIN_2_4 -> {}
            kgp >= KOTLIN_2_3 -> compilerArgs.add("-Xexplicit-backing-fields")
            else -> compilerArgs.add(langFeature("ExplicitBackingFields"))
        }
    }

    compilerArgs.addDefault(KotlinDefault.DONT_WARN_ON_ERROR_SUPPRESSION)

    // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
    if (!releaseSettings && context.useKotlinDebug) {
        compilerArgs.add("-Xdebug")
    }

    freeCompilerArgs.set(compilerArgs.toList())
}


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

    // Compile using Front-end IR internal incremental compilation cycle.
    // Warning: this feature is far from being production-ready.
    "-Xuse-fir-ic",

    // Check pre- and postconditions on phases.
    "-Xcheck-phase-conditions",
).asList()

private val LATEST_JVM_OPTS = arrayOf(
    // Allow using features from Java language that are in the preview phase.
    // Works as `--enable-preview` in Java.
    // All class files are marked as preview-generated, thus it won't be possible to use
    //  them in the release environment.
    "-Xjvm-enable-preview",
).asList()

// Remove utility bytecode, eliminating names/data leaks in release obfuscated code.
// https://proandroiddev.com/kotlin-cleaning-java-bytecode-before-release-9567d4c63911
// https://www.guardsquare.com/blog/eliminating-data-leaks-caused-by-kotlin-assertions
private val JVM_RELEASE_OPTS = arrayOf(
    "-Xno-call-assertions",
    "-Xno-param-assertions",
    "-Xno-receiver-assertions",
).asList()

// TODO: -Xwasm-use-new-exception-proposal
//  https://kotlinlang.org/docs/whatsnew20.html#new-exception-handling-proposal-is-now-supported-under-the-option
