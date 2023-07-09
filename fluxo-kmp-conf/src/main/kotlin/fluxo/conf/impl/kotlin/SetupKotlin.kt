@file:Suppress("CyclomaticComplexMethod")

package fluxo.conf.impl.kotlin

import ALIAS_ANDROIDX_COMPOSE_COMPILER
import KAPT_PLUGIN_ID
import KSP_PLUGIN_ID
import MAIN_SOURCE_SET_NAME
import TEST_SOURCE_SET_NAME
import fluxo.conf.deps.loadAndApplyPluginIfNotApplied
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.addAll
import fluxo.conf.impl.configureExtensionIfAvailable
import fluxo.conf.impl.d
import fluxo.conf.impl.get
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.l
import fluxo.conf.impl.onVersion
import fluxo.conf.impl.v
import fluxo.conf.impl.version
import hasKapt
import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.base.plugins.LifecycleBasePlugin.CHECK_TASK_NAME
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget


internal fun KotlinProjectExtension.setupKotlinExtension(
    conf: FluxoConfigurationExtensionImpl,
) {
    val project = conf.project
    val logger = project.logger
    logger.v("Configuring Kotlin extension")

    val ctx = conf.context
    val kc = conf.KotlinConfig(project)

    if (kc.setupKsp && !project.hasKsp) {
        ctx.loadAndApplyPluginIfNotApplied(id = KSP_PLUGIN_ID, project = project)
    }
    if (kc.setupKapt && !project.hasKapt) {
        ctx.loadAndApplyPluginIfNotApplied(id = KAPT_PLUGIN_ID, project = project)
    }

    setupJvmCompatibility(project, kc)

    kc.coreLibs?.let { coreLibrariesVersion = it }

    setupTargets(conf, kc)
    setupSourceSets(kc)

    // TODO: Check KSP setup for KMP modules
    if (kc.setupKsp && this is KotlinJvmProjectExtension) {
        sourceSets[MAIN_SOURCE_SET_NAME].apply {
            kotlin.srcDir("build/generated/ksp/main/kotlin")
            resources.srcDir("build/generated/ksp/main/resources")
        }
        sourceSets[TEST_SOURCE_SET_NAME].kotlin.srcDir("build/generated/ksp/test/kotlin")
    }
}

private fun KotlinProjectExtension.setupSourceSets(
    kc: KotlinConfig,
    testOptIns: Set<String> = kc.prepareTestOptIns(),
) = sourceSets.all {
    val isTest = isTestRelated()
    languageSettings {
        val (lang, api) = kc.langAndApiVersions(isTest = isTest)
        lang?.run { languageVersion = version }
        api?.run { apiVersion = version }

        if (kc.progressive && lang.isCurrentOrLater) {
            progressiveMode = true
        }

        (if (isTest) testOptIns else kc.optIns)
            .forEach(::optIn)
    }
}

private fun KotlinProjectExtension.setupTargets(
    conf: FluxoConfigurationExtensionImpl,
    kc: KotlinConfig,
) = setupTargets {
    val compilations = compilations

    // Experimental test compilation with the latest Kotlin settings.
    if (kc.latestCompilation && platformType != KotlinPlatformType.common) {
        compilations.all {
            if (name == MAIN_SOURCE_SET_NAME) {
                val main = this
                compilations.create(EXPERIMENTAL_TEST_COMPILATION_NAME) {
                    defaultSourceSet.dependsOn(main.defaultSourceSet)
                    conf.project.tasks.named(CHECK_TASK_NAME) {
                        dependsOn(compileTaskProvider)
                    }
                }
            }
        }
    }

    compilations.all {
        val name = name
        val latestSettings = name == EXPERIMENTAL_TEST_COMPILATION_NAME

        val isTest = isTestRelated()
        val isReleaseTask = name.contains("Release", ignoreCase = true)
        val isJs = target.platformType
            .let { KotlinPlatformType.js == it || KotlinPlatformType.wasm == it }

        val context = conf.context
        val warningsAsErrors = kc.warningsAsErrors &&
            !isJs && !isTest && (context.isCI || context.isRelease)

        val jvmTargetVersion = kc.jvmTargetVersion(isTest = isTest, latestSettings = latestSettings)
        kotlinOptions {
            if (warningsAsErrors) {
                allWarningsAsErrors = true
            }

            val (lang, api) = kc
                .langAndApiVersions(isTest = isTest, latestSettings = latestSettings)
            lang?.run { languageVersion = version }
            api?.run { apiVersion = version }
            if ((kc.progressive || latestSettings) && lang.isCurrentOrLater) {
                freeCompilerArgs += "-progressive"
            }

            setupKotlinOptions(
                conf = conf,
                isReleaseTask = isReleaseTask,
                warningsAsErrors = warningsAsErrors,
                jvmTargetVersion = jvmTargetVersion,
                kc = kc,
            )
        }

        jvmTargetVersion?.let { v ->
            compileJavaTaskProvider?.configure {
                sourceCompatibility = v
                targetCompatibility = v
            }
        }

        if (isTest && context.testsDisabled) {
            disableCompilation()
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
private fun KotlinCommonOptions.setupKotlinOptions(
    conf: FluxoConfigurationExtensionImpl,
    isReleaseTask: Boolean,
    warningsAsErrors: Boolean,
    jvmTargetVersion: String?,
    kc: KotlinConfig,
) {
    val context = conf.context
    val isCI = context.isCI
    val isRelease = context.isRelease
    val releaseSettings = isCI || isRelease || isReleaseTask

    val freeCompilerArgs = freeCompilerArgs.toMutableList()

    // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
    // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/js/jsExtraHelp.out
    freeCompilerArgs.addAll(
        arrayOf(
            "-Xcontext-receivers",
            "-Xklib-enable-signature-clash-checks",
        ),
    )

    if (this is KotlinJvmOptions) {
        jvmTargetVersion?.let { jvmTarget = it }
        javaParameters = kc.javaParameters

        // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
        freeCompilerArgs.addAll(
            "-Xjsr305=strict",
            "-Xjvm-default=all",
            "-Xtype-enhancement-improvements-strict-mode",
            "-Xvalidate-bytecode",
            "-Xvalidate-ir",
        )

        // Using the new faster version of JAR FS should make build faster,
        // but it is experimental and causes warning.
        if (!warningsAsErrors && kc.useExperimentalFastJarFs) {
            freeCompilerArgs.add("-Xuse-fast-jar-file-system")
        }

        // class mode provides lambdas arguments names
        (if (kc.useIndyLambdas || isCI || releaseSettings) "indy" else "class").let {
            freeCompilerArgs.addAll("-Xlambdas=$it", "-Xsam-conversions=$it")
        }

        // Remove utility bytecode, eliminating names/data leaks in release obfuscated code.
        // https://proandroiddev.com/kotlin-cleaning-java-bytecode-before-release-9567d4c63911
        // https://www.guardsquare.com/blog/eliminating-data-leaks-caused-by-kotlin-assertions
        if (releaseSettings && kc.removeAssertionsInRelease) {
            freeCompilerArgs.addAll(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions",
            )
        }
    }

    // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
    if (!releaseSettings && context.useKotlinDebug) {
        freeCompilerArgs.add("-Xdebug")
    }

    val libs = context.libs
    libs?.onVersion(ALIAS_ANDROIDX_COMPOSE_COMPILER) { _ ->
        val p = "plugin:androidx.compose.compiler.plugins.kotlin"
        if (kc.suppressKotlinComposeCompatCheck) {
            val kotlin = libs.version("kotlin")
            freeCompilerArgs.addAll("-P", "$p:suppressKotlinVersionCompatibilityCheck=$kotlin")
        }

        val isMaxDebug = context.isMaxDebug
        val composeMetricsEnabled = context.composeMetricsEnabled
        @Suppress("MaxLineLength", "ArgumentListWrapping", "ComplexCondition")
        if (isCI || isRelease || composeMetricsEnabled || isMaxDebug) {
            // https://chris.banes.dev/composable-metrics/
            // https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md#interpreting-compose-compiler-metrics
            val reportsDir = "${conf.project.buildDir.absolutePath}/reports/compose"
            freeCompilerArgs.addAll(
                "-P", "$p:metricsDestination=$reportsDir",
                "-P", "$p:reportsDestination=$reportsDir",
            )

            // Convert the report to human-readable html.
            // https://patilshreyas.github.io/compose-report-to-html/
            // $ composeReport2Html -app LinenWallet -overallStatsReport app_primaryDebug-module.json -detailedStatsMetrics app_primaryDebug-composables.csv -composableMetrics app_primaryDebug-composables.txt -classMetrics app_primaryDebug-classes.txt -o htmlReportDebug
            // $ composeReport2Html -app LinenWallet -overallStatsReport app_primaryRelease-module.json -detailedStatsMetrics app_primaryRelease-composables.csv -composableMetrics app_primaryRelease-composables.txt -classMetrics app_primaryRelease-classes.txt -o htmlReportRelease
        }
    }
}

private const val EXPERIMENTAL_TEST_COMPILATION_NAME = "experimentalTest"

private fun KotlinProjectExtension.setupTargets(action: Action<in KotlinTarget>) {
    when (this) {
        is KotlinSingleTargetExtension<*> -> action.execute(target)
        is KotlinMultiplatformExtension -> targets.all(action)
    }
}

private fun KotlinProjectExtension.setupJvmCompatibility(
    project: Project,
    kc: KotlinConfig,
) = setupJvmCompatibility(project, kc.jvmTarget, kc.jvmToolchain)

internal fun KotlinProjectExtension.setupJvmCompatibility(
    project: Project,
    jvmTarget: String?,
    jvmToolchain: Boolean,
) {
    if (this is KotlinSingleTargetExtension<*>
        && target.run { this is KotlinJvmTarget && !withJavaEnabled }
    ) {
        project.logger.d("KotlinSingleTarget with no Java enabled, skip Java compatibility setup")
        return
    }

    if (jvmTarget == null) {
        project.logger.l("Java compatibility is not explicitly set")
    } else if (jvmToolchain) {
        // Kotlin set up toolchain for java automatically
        jvmToolchain(jvmTarget.asJvmMajorVersion())
    } else {
        project.configureExtensionIfAvailable<JavaPluginExtension> {
            JavaVersion.toVersion(jvmTarget).let { v ->
                sourceCompatibility = v
                targetCompatibility = v
            }
        }

        // Global tasks configuration isn't applied (project.tasks.withType<JavaCompile>)
        // as more fine-grained configuration is preferred.
    }
}


internal fun KotlinCompilation<*>.disableCompilation() {
    val c = this
    val function: Action<in Task> = Action {
        if (enabled) {
            enabled = false
            logger.d("task ':{}:{}' disabled, {}", project.name, name, c)
        }
    }
    compileTaskProvider.configure(function)
    compileJavaTaskProvider?.configure(function)
}

/** @see org.jetbrains.kotlin.gradle.plugin.findJavaTaskForKotlinCompilation */
private val KotlinCompilation<*>.compileJavaTaskProvider: TaskProvider<out JavaCompile>?
    get() = when (this) {
        is KotlinJvmAndroidCompilation -> compileJavaTaskProvider
        is KotlinWithJavaCompilation<*, *> -> compileJavaTaskProvider
        // nullable for Kotlin-only JVM target in MPP
        is KotlinJvmCompilation -> compileJavaTaskProvider
        else -> null
    }
