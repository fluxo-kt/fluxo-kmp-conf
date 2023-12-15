package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAll
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJsOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion

@Suppress("LongParameterList", "ComplexMethod", "LongMethod")
internal fun KotlinCommonOptions.setupKotlinOptions(
    conf: FluxoConfigurationExtensionImpl,
    compilationName: String,
    warningsAsErrors: Boolean,
    latestSettings: Boolean,
    isAndroid: Boolean,
    isTest: Boolean,
    isMultiplatform: Boolean,
    jvmTargetVersion: String?,
) {
    val context = conf.context
    val isCI = context.isCI
    val isRelease = context.isRelease
    val isReleaseTask = compilationName.contains("Release", ignoreCase = true)
    val releaseSettings = isCI || isRelease || isReleaseTask
    val useLatestSettings = !releaseSettings && latestSettings
    val kc = context.kotlinConfig

    val compilerArgs = freeCompilerArgs.toMutableList()
    compilerArgs.addAll(DEFAULT_OPTS)

    val kotlinPluginVersion = context.kotlinPluginVersion
    val (lang) = kc.langAndApiVersions(isTest = isTest, latestSettings = useLatestSettings)
    if (if (lang != null) lang <= KotlinLangVersion.KOTLIN_1_9 else kotlinPluginVersion <= KOTLIN_1_9) {
        compilerArgs.addAll(KOTLIN_UP_TO_1_9_OPTS)
    }

    if (useLatestSettings) {
        compilerArgs.addAll(LATEST_OPTS)
    }

    if (isMultiplatform && kotlinPluginVersion >= KOTLIN_1_9_20) {
        compilerArgs.add("-Xexpect-actual-classes")
    }

    val isJvm: Boolean
    val isJs: Boolean
    when (this) {
        is KotlinJvmOptions -> {
            isJvm = true
            isJs = false

            // FIXME: Move to JvmCompatibility?
            jvmTargetVersion?.let {
                setupJvmCompatibility(it)

                // Compile against the specified JDK API version, similarly to javac's `-release`.
                // Controls the target bytecode version and limits the API of the JDK in the
                // classpath to the specified Java version.
                // Requires Kotlin 1.7.0 or newer and JDK 9 or newer.
                // This option isn't guaranteed to be effective for each JDK distribution.
                // https://kotlinlang.org/docs/whatsnew17.html#new-compiler-option-xjdk-release
                // https://youtrack.jetbrains.com/issue/KT-29974
                // FIXME: Move to KotlinConfig and configure in the context
                //  by default (null) only for useLatestSettings, true/false for broader control.
                if (useLatestSettings &&
                    JRE_VERSION >= JRE_1_9 &&
                    kotlinPluginVersion >= KOTLIN_1_7
                // TODO: && kotlinLang >= KotlinLangVersion.KOTLIN_1_7
                ) {
                    compilerArgs.add("-Xjdk-release=$it")

                    // TODO: Allow -Xjdk-release=1.6 with -jvm-target 1.8 once it is supported
                    //  https://youtrack.jetbrains.com/issue/KT-59098/Support-Xjdk-release1.6-with-jvm-target-1.8
                }
            }

            if (kc.javaParameters) javaParameters = true

            compilerArgs.addAll(JVM_OPTS)
            if (useLatestSettings) compilerArgs.addAll(LATEST_JVM_OPTS)

            /** @see ANDROID_SAFE_JVM_TARGET */
            if (isAndroid && kc.useSafeAndroidOptions) compilerArgs.add("-Xstring-concat=inline")

            // Using the new faster version of JAR FS should make build faster,
            // but it is experimental and causes warning.
            if (!warningsAsErrors && kc.fastJarFs) compilerArgs.add("-Xuse-fast-jar-file-system")

            // class mode provides lambdas arguments names
            // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
            val useIndyLambdas = kc.jvmTargetInt >= JRE_1_8 &&
                (kc.useIndyLambdas || isCI || releaseSettings)
            (if (useIndyLambdas) "indy" else "class").let { mode ->
                compilerArgs.addAll("-Xlambdas=$mode", "-Xsam-conversions=$mode")
            }

            if (releaseSettings && kc.removeAssertionsInRelease) {
                compilerArgs.addAll(JVM_RELEASE_OPTS)
            }
        }

        is KotlinJsOptions -> {
            isJs = true
            isJvm = false
            compilerArgs.addAll(JS_OPTS)
        }

        else -> {
            isJvm = false
            isJs = false
        }
    }

    if (useLatestSettings) {
        // Compile using experimental K2.
        // K2 is a new compiler pipeline with no compatibility guarantees.
        // K2 doesn't support all targets and all compiler features yet.
        if (isJvm || isJs) compilerArgs.add("-Xuse-k2")

        // Lang features
        /** @see org.jetbrains.kotlin.config.LanguageFeature */
        // https://github.com/JetBrains/kotlin/blob/ca0b061/compiler/util/src/org/jetbrains/kotlin/config/LanguageVersionSettings.kt

        // Non-local break and continue are in preview since 2.0
        // https://youtrack.jetbrains.com/issue/KT-1436/Support-non-local-break-and-continue
        if (languageVersion.let { it != null && it.startsWith("2.") }) {
            compilerArgs.add(langFeature("BreakContinueInInlineLambdas"))
        }
    }

    // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
    if (!releaseSettings && context.useKotlinDebug) compilerArgs.add("-Xdebug")

    if (kc.setupCompose) conf.setupKotlinComposeOptions(compilerArgs)

    freeCompilerArgs = compilerArgs
}

private fun FluxoConfigurationExtensionImpl.setupKotlinComposeOptions(
    compilerArgs: MutableList<String>,
) {
    val ctx = context
    val p = "plugin:androidx.compose.compiler.plugins.kotlin"
    if (suppressKotlinComposeCompatibilityCheck == true) {
        val kotlin = ctx.kotlinPluginVersion.toString()
        compilerArgs.addAll("-P", "$p:suppressKotlinVersionCompatibilityCheck=$kotlin")
    }

    @Suppress("ComplexCondition")
    if (!ctx.isCI && !ctx.isRelease && !ctx.composeMetricsEnabled && !ctx.isMaxDebug) {
        return
    }

    // Output Compose Compiler metrics to the specified directory.
    // https://chris.banes.dev/composable-metrics/
    // https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md#interpreting-compose-compiler-metrics
    val buildDir = project.layout.buildDirectory.get().asFile.absolutePath
    val reportsDir = "$buildDir/reports/compose"
    compilerArgs.addAll("-P", "$p:metricsDestination=$reportsDir")
    compilerArgs.addAll("-P", "$p:reportsDestination=$reportsDir")

    @Suppress("MaxLineLength")
    // Note: convert the report to the human-readable HTML.
    // https://patilshreyas.github.io/compose-report-to-html/
    // TODO: Make conversion of Compose Compiler metrics to HTML automatically with Gradle.
    // $ composeReport2Html -app LinenWallet -overallStatsReport app_primaryDebug-module.json -detailedStatsMetrics app_primaryDebug-composables.csv -composableMetrics app_primaryDebug-composables.txt -classMetrics app_primaryDebug-classes.txt -o htmlReportDebug
    // $ composeReport2Html -app LinenWallet -overallStatsReport app_primaryRelease-module.json -detailedStatsMetrics app_primaryRelease-composables.csv -composableMetrics app_primaryRelease-composables.txt -classMetrics app_primaryRelease-classes.txt -o htmlReportRelease
    return
}

private fun langFeature(name: String) = "-XXLanguage:+$name"


// https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
// https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/js/jsExtraHelp.out
private val DEFAULT_OPTS = arrayOf(
    // Check uniqueness of signatures at klib generating phase
    "-Xklib-enable-signature-clash-checks",

    // Enable context receivers. Only works for JVM targets by 2023.07.
    "-Xcontext-receivers",
).asList()

// Not supported in Kotlin 2.1 language version.
private val KOTLIN_UP_TO_1_9_OPTS = arrayOf(
    // For incremental compilation, both flags should be supplied:
    //  -Xenable-incremental-compilation and -Xic-cache-dir.
    "-Xenable-incremental-compilation",

    // Inline functions using IR inliner instead of bytecode inliner for JVM targets.
    "-Xir-inliner",

    // Support for IR backend parallel compilation:
    //  https://youtrack.jetbrains.com/issue/KT-46085
    // IR backend runs lowerings by file in N parallel threads.
    //  0 means use a thread per processor core.
    //  The default value is 1.
    // Using /2 processors as JVM and Android targets share the processors
    // and compile at the same time.
    // Also reserved 2 cores for Gradle multitasking.
    // On Apple M1 Max parallelism reduces compilation time by 1/3.
    // Seems to be not supported in Kotlin 2.0+ and failing in Kotlin 1.9.21.
    // "-Xbackend-threads=" + (CPUs / 2 - 1).coerceAtLeast(1),
).asList()

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

    // Use extended analysis mode based on Front-end IR.
    // Warning: this feature is far from being production-ready.
    "-Xuse-fir-extended-checkers",

    // Check pre- and postconditions on phases.
    "-Xcheck-phase-conditions",
    // Run sticky condition checks on next phases as well.
    "-Xcheck-sticky-phase-conditions",

    // Use the builder inference by default,
    // for all calls with lambdas, which can't be resolved without it.
    // The corresponding calls' declarations may not be marked with @BuilderInference.
    "-Xenable-builder-inference",

    // Enable experimental language feature: effect system.
    "-Xeffect-system",

    // Enable new experimental generic type inference algorithm.
    "-Xnew-inference",

    // Support inferring type arguments based on only self-upper-bounds
    // of the corresponding type parameters.
    "-Xself-upper-bound-inference",

    // Enable extra compiler checks that might provide verbose diagnostic information
    // for certain errors.
    // Warning: this mode isn't backward-compatible and might cause compilation errors.
    "-Xextended-compiler-checks",
).asList()

private val LATEST_JVM_OPTS = arrayOf(
    // Enhance not null annotated type parameter's types to definitely not null types
    // (@NotNull T => T & Any)
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
    "-Xvalidate-ir",
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
