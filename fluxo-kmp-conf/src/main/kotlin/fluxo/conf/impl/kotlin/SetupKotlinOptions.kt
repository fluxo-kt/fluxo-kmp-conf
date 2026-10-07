package fluxo.conf.impl.kotlin

import fluxo.annotation.VersionGated
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAll
import org.gradle.api.provider.Provider
import org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJsCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget

/**
 * The defaults every compilation of the module shares, as conventions on the module's own
 * `kotlin { compilerOptions }`. KGP passes module values down to targets and compile tasks as
 * conventions too, so a value the consumer sets on the module, a target or a task wins, written
 * before or after `fkcSetup*()`. A KMP module's own options are common-only; JVM and JS options
 * go on each target ([setupTargetKotlinOptions]).
 */
internal fun KotlinProjectExtension.setupModuleKotlinOptions(
    conf: FluxoConfigurationExtensionImpl,
) {
    val options = (this as? HasConfigurableKotlinCompilerOptions<*>)?.compilerOptions ?: return
    val kc = conf.kotlinConfig
    val ctx = conf.ctx
    if (kc.warningsAsErrors && (ctx.isCI || ctx.isRelease)) {
        options.allWarningsAsErrors.convention(true)
    }
    val (lang, api) = kc.langAndApiVersions(isTest = false)
    lang?.let { options.languageVersion.convention(it) }
    api?.let { options.apiVersion.convention(it) }
    if (kc.progressive && lang.isCurrentOrLater) {
        options.progressiveMode.convention(true)
    }
    if (KotlinDefault.EXTRA_WARNINGS !in kc.defaultsOff) {
        options.extraWarnings.convention(true)
    }
    options.optIn.addAll(kc.optIns)
    (options as? KotlinJvmCompilerOptions)?.setupJvmModuleOptions(conf)
}

/** JVM and JS defaults of a KMP target: the module-level options of KMP have none of them. */
internal fun KotlinTarget.setupTargetKotlinOptions(conf: FluxoConfigurationExtensionImpl) {
    when (val options = (this as? HasConfigurableKotlinCompilerOptions<*>)?.compilerOptions) {
        is KotlinJvmCompilerOptions -> options.setupJvmModuleOptions(conf)

        // ES2015 classes under the Kotlin plugin's default ES5 target. JS only: Wasm targets
        // share this options type, but Kotlin 2.4 compiles Wasm with its own argument set, where
        // a JS-only flag warns "not supported by this version of the compiler".
        // `-Xoptimize-generated-js` is on by default on the whole supported range.
        is KotlinJsCompilerOptions -> if (platformType === KotlinPlatformType.js) {
            options.useEsClasses.convention(true)
        }

        else -> {}
    }
}

private fun KotlinJvmCompilerOptions.setupJvmModuleOptions(conf: FluxoConfigurationExtensionImpl) {
    if (conf.kotlinConfig.javaParameters) {
        javaParameters.convention(true)
    }
    // The typed `jvmDefault` option exists only since KGP 2.2; the plugin is APPLIED against the
    // consumer's KGP and the layer-2 floor is Kotlin 2.1, where calling the 2.2 getter throws
    // NoSuchMethodError. Below 2.2, `setupKotlinOptions` passes `-Xjvm-default=all`, the 2.1
    // equivalent of `NO_COMPATIBILITY`, deprecated on 2.2+ (it would trip `-Werror`).
    if (conf.ctx.kotlinPluginVersion >= KOTLIN_2_2) {
        jvmDefaultNoCompatibility()
    }
}

@VersionGated
private fun KotlinJvmCompilerOptions.jvmDefaultNoCompatibility() {
    jvmDefault.convention(JvmDefaultMode.NO_COMPATIBILITY)
}

/**
 * Per-compilation settings, on the compile task (the same options object as its compilation).
 * Values that differ from the module's defaults (test or experimental language version, and
 * warnings-as-errors off) are conventions here, so they lose to a value set on this task or
 * compilation, but a module or target value no longer reaches this compilation. Flags are added
 * lazily, without those the consumer already passes for the module or target
 * ([addArgsUnlessInherited]).
 */
@Suppress("LongParameterList", "ComplexMethod", "LongMethod")
internal fun KotlinCommonCompilerOptions.setupKotlinOptions(
    conf: FluxoConfigurationExtensionImpl,
    compilationName: String,
    warningsAsErrorsOff: Boolean,
    latestSettings: Boolean,
    isAndroid: Boolean,
    isMultiplatform: Boolean,
    jvmTargetVersion: String?,
    inheritedArgs: Provider<List<String>>?,
    optInPlatform: OptInPlatform?,
    coroutinesOptIns: Provider<List<String>>?,
) {
    val context = conf.ctx
    val isCI = context.isCI
    val isRelease = context.isRelease
    val isReleaseTask = compilationName.contains("Release", ignoreCase = true)
    val releaseSettings = isCI || isRelease || isReleaseTask
    val useLatestSettings = !releaseSettings && latestSettings
    val kc = conf.kotlinConfig

    if (warningsAsErrorsOff) {
        allWarningsAsErrors.convention(false)
    }

    val compilerArgs = LinkedHashSet(DEFAULT_OPTS)
    coroutinesOptIns?.let(optIn::addAll)
    optIn.addAll(kc.platformOptIns.forPlatform(optInPlatform))

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
        compilerArgs.addDefault(KotlinDefault.EXPECT_ACTUAL_CLASSES, kc.defaultsOff)
    }

    // Strict defaults. Each is passed only where the consumer's compiler has it and it is not
    // already the default at the compilation's language version (see KotlinDefault).
    val kgp = context.kotlinPluginVersion
    val effectiveLang = languageVersion.orNull?.version?.let(::parseKotlinPluginVersion)
        ?: KotlinVersion(kgp.major, kgp.minor)
    if (kgp >= KOTLIN_2_3) {
        compilerArgs.addDefault(KotlinDefault.RETURN_VALUE_CHECKER, kc.defaultsOff)
    }
    if (effectiveLang < KOTLIN_2_4) {
        compilerArgs.addDefault(KotlinDefault.ANNOTATION_DEFAULT_TARGET, kc.defaultsOff)
    }
    compilerArgs.addDefault(KotlinDefault.CONSISTENT_DATA_CLASS_COPY_VISIBILITY, kc.defaultsOff)

    // Read from the compile task's own options, which inherit the module's and target's, so a
    // version the consumer set in their own `kotlin { compilerOptions }` counts too.
    val deprecatedVersions = listOfNotNull(languageVersion.orNull, apiVersion.orNull)
        .distinct().filter { it.isDeprecatedByKgp }
    // Switched off, the compiler's own warning stays, so the build-end replacement is not needed.
    if (deprecatedVersions.isNotEmpty() &&
        compilerArgs.addDefault(KotlinDefault.SUPPRESS_VERSION_WARNINGS, kc.defaultsOff)
    ) {
        val path = conf.project.path
        deprecatedVersions.forEach { context.deprecatedKotlinVersions.record(it, path) }
    }

    when (this) {
        is KotlinJvmCompilerOptions -> {
            if (jvmTargetVersion == null && isAndroid) {
                followAndroidJavaTarget(conf.project)
            }
            // The JDK API limit (-Xjdk-release) is added per task by `limitKotlinJdkApi`.
            // Set, not a convention: KGP gives the task a toolchain-derived convention, and
            // javac's target is set from the same value, so the two can't disagree. The
            // module's `jvmTarget` setting is how a consumer changes it.
            jvmTargetVersion?.let { setupJvmCompatibility(it) }

            // KGP 2.1: see `setupJvmModuleOptions`.
            if (kgp < KOTLIN_2_2) {
                compilerArgs.add("-Xjvm-default=all")
            }

            compilerArgs.addDefault(KotlinDefault.EMIT_JVM_TYPE_ANNOTATIONS, kc.defaultsOff)
            compilerArgs.addDefault(KotlinDefault.JSR305, kc.defaultsOff)
            compilerArgs.addDefault(KotlinDefault.VALIDATE_BYTECODE, kc.defaultsOff)
            val target = jvmTargetVersion?.toJvmMajorVersion() ?: 0
            if (kgp >= KOTLIN_2_2 && kgp < KOTLIN_2_4 && target >= JRE_21) {
                compilerArgs.addDefault(KotlinDefault.WHEN_EXPRESSIONS_INDY, kc.defaultsOff)
            }
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
        when {
            kgp >= KOTLIN_2_4 -> {}
            kgp >= KOTLIN_2_3 -> compilerArgs.add("-Xexplicit-backing-fields")
            else -> compilerArgs.add(langFeature("ExplicitBackingFields"))
        }
    }

    compilerArgs.addDefault(KotlinDefault.DONT_WARN_ON_ERROR_SUPPRESSION, kc.defaultsOff)

    // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
    if (!releaseSettings && context.useKotlinDebug) {
        compilerArgs.add("-Xdebug")
    }

    addArgsUnlessInherited(compilerArgs.toList(), inheritedArgs)
}

/**
 * Adds fluxo's [args] to a compile task lazily, without any whose key (the part before `=`) the
 * consumer passes for the whole module or target ([inherited]): their value wins, and no flag
 * reaches the compiler twice with different values. That is an error for some flags
 * (`-Xjsr305`: "Conflict duplicating") and a "passed multiple times" warning for most, which
 * fails `-Werror` builds. A flag the consumer adds on the task itself is not seen here.
 */
internal fun KotlinCommonCompilerOptions.addArgsUnlessInherited(
    args: List<String>,
    inherited: Provider<List<String>>?,
) {
    when {
        args.isEmpty() -> {}
        inherited == null -> freeCompilerArgs.addAll(args)
        else -> freeCompilerArgs.addAll(inherited.map { withoutInheritedKeys(args, it) })
    }
}

private fun withoutInheritedKeys(args: List<String>, inherited: List<String>): List<String> {
    val keys = inherited.mapTo(HashSet()) { it.substringBefore('=') }
    return args.filter { it.substringBefore('=') !in keys }
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
