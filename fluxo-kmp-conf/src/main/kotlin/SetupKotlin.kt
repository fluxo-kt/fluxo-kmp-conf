@file:Suppress("ktPropBy")

import fluxo.conf.impl.compileOnlyWithConstraint
import fluxo.conf.impl.exclude
import fluxo.conf.impl.get
import fluxo.conf.impl.implementation
import fluxo.conf.impl.isTestRelated
import fluxo.conf.impl.kotlin
import fluxo.conf.impl.kotlin.disableCompilation
import fluxo.conf.impl.kotlin.setupJvmCompatibility
import fluxo.conf.impl.libsCatalog
import fluxo.conf.impl.onBundle
import fluxo.conf.impl.onLibrary
import fluxo.conf.impl.onVersion
import fluxo.conf.impl.testImplementation
import fluxo.conf.impl.v
import fluxo.conf.impl.version
import fluxo.conf.impl.withType
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.Kotlin2JsProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJsProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet

public fun Project.setupKotlin(
    config: KotlinConfigSetup = requireDefaultKotlinConfigSetup(),
    setupKsp: Boolean = hasKsp,
    optIns: List<String> = emptyList(),
    body: (KotlinSingleTargetExtension<*>.() -> Unit)? = null,
) {
    setupKotlin0(config = config, setupKsp = setupKsp, optIns = optIns, body = body)
    kotlinExtension.disableCompilationsOfNeeded(project)
}

internal fun Project.setupKotlin0(
    config: KotlinConfigSetup = requireDefaultKotlinConfigSetup(),
    setupKsp: Boolean = hasKsp,
    optIns: List<String> = emptyList(),
    body: (KotlinSingleTargetExtension<*>.() -> Unit)? = null,
) {
    val kotlin = kotlinExtension
    require(kotlin is KotlinJvmProjectExtension || kotlin is KotlinAndroidProjectExtension) {
        when (kotlin) {
            is KotlinMultiplatformExtension -> "use `setupMultiplatform` for KMP module"

            is KotlinJsProjectExtension, is Kotlin2JsProjectExtension ->
                "use `setupJsApp` for Kotlin/JS module"

            else -> "unexpected KotlinProjectExtension: $kotlin"
        }
    }
    setupKotlinExtension(kotlin = kotlin, setupKsp = setupKsp, config = config, optIns = optIns)
    dependencies.setupKotlinDependencies(project = this, config = config)

    body?.invoke(kotlin as KotlinSingleTargetExtension<*>)
}

/**
 *
 * @see fluxo.conf.impl.setupKotlinExtension
 */
@Deprecated(message = "see fluxo.conf.impl.setupKotlinExtension")
internal fun Project.setupKotlinExtension(
    kotlin: KotlinProjectExtension,
    setupKsp: Boolean = hasKsp,
    config: KotlinConfigSetup = requireDefaultKotlinConfigSetup(),
    optIns: List<String> = emptyList(),
) {
    val libs = libsCatalog
    kotlin.setupJvmCompatibility(
        project = this,
        jvmTarget = libs.getJavaLangTarget(config),
        jvmToolchain = config.setupJvmToolchain,
    )

    // Duplicate languageSettings here as the IDE doesn't catch settings from compile tasks.
    val disableTests by disableTests()
    val kotlinLangVersion = config.getKotlinLangVersion(libs)
    logger.lifecycle("> Conf Kotlin language and API ${kotlinLangVersion.version}")
    val sourceSets = kotlin.sourceSets
    sourceSets.all {
        val isTestSet = isTestRelated()

        if (isTestSet && disableTests && this is AbstractKotlinSourceSet) {
            compilations.forEach { it.disableCompilation() }
        }

        languageSettings {
            // https://kotlinlang.org/docs/compatibility-modes.html
            languageVersion = kotlinLangVersion.version
            apiVersion = kotlinLangVersion.version

            config.getListOfOptIns(isTest = isTestSet, optIns)
                .forEach(::optIn)

            val isLatestKotlinVersion = kotlinLangVersion == KotlinVersion.values().last()
            if (config.progressiveMode && isLatestKotlinVersion) {
                progressiveMode = true
            }
        }
    }

    if (setupKsp && kotlin is KotlinJvmProjectExtension) {
        sourceSets[MAIN_SOURCE_SET_NAME].apply {
            this.kotlin.srcDir("build/generated/ksp/main/kotlin")
            resources.srcDir("build/generated/ksp/main/resources")
        }
        sourceSets[TEST_SOURCE_SET_NAME].kotlin.srcDir("build/generated/ksp/test/kotlin")
    }

    setupKotlinTasks(config, optIns)
}

private fun Project.setupKotlinTasks(config: KotlinConfigSetup, optIns: List<String>) =
    afterEvaluate {
        val isCI by isCI()
        val isRelease by isRelease()
        val disableTests by disableTests()
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>> {
            val taskName = name
            val isJsTask = "Js" in taskName
            val isTestTask = isTestRelated()
            val isReleaseTask = "Release" in taskName
            val warningsAsErrors = config.warningsAsErrors &&
                !isJsTask && !isTestTask && (isCI || isRelease)

            if (isTestTask && disableTests) {
                enabled = false
            }

            compilerOptions {
                if (warningsAsErrors) {
                    allWarningsAsErrors.set(true)
                }
                setupKotlinOptions(
                    project = project,
                    isReleaseTask = isReleaseTask,
                    isTestTask = isTestTask,
                    warningsAsErrors = warningsAsErrors,
                    config = config,
                    optIns = optIns,
                )
            }
        }
    }

internal fun DependencyHandler.setupKotlinDependencies(
    project: Project,
    config: KotlinConfigSetup = project.requireDefaultKotlinConfigSetup(),
) {
    val libs = project.libsCatalog

    if (config.addStdlibDependency) {
        implementation(kotlin("stdlib", libs.v("kotlin")))
    }

    compileOnlyWithConstraint(JSR305_DEPENDENCY)
    libs.onLibrary("jetbrains-annotation") { compileOnlyWithConstraint(it) }

    val kotlinBom = enforcedPlatform(kotlin("bom", libs.v("kotlin")))
    when {
        config.setupKnownBoms -> implementation(kotlinBom)
        else -> testImplementation(kotlinBom)
    }
    testImplementation(kotlin("test-junit"))


    val hasCoroutinesBom = libs.onLibrary("kotlinx-coroutines-bom") {
        if (config.setupKnownBoms) {
            implementation(enforcedPlatform(it), excludeAnnotations)
            if (config.setupCoroutines) {
                implementation(COROUTINES_DEPENDENCY)
            }
        } else {
            testImplementation(enforcedPlatform(it))
            if (config.setupCoroutines) {
                testImplementation(COROUTINES_DEPENDENCY)
            }
        }
    } && config.setupKnownBoms
    if (config.setupCoroutines) {
        if (!hasCoroutinesBom) {
            libs.onLibrary("kotlinx-coroutines-core") { implementation(it) }
        }
        libs.onLibrary("kotlinx-coroutines-test") { testImplementation(it) }
        libs.onLibrary("kotlinx-coroutines-debug") { testImplementation(it) }
    }

    if (config.setupSerialization) {
        libs.onLibrary("kotlinx-serialization-bom") { implementation(enforcedPlatform(it)) }
    }

    libs.onLibrary("ktor-bom") { implementation(enforcedPlatform(it)) }
    libs.onLibrary("arrow-bom") { implementation(enforcedPlatform(it)) }
    val hasOkioBom = libs.onLibrary("square-okio-bom") { implementation(enforcedPlatform(it)) }
    val hasOkhttpBom = libs.onLibrary("square-okhttp-bom") { implementation(enforcedPlatform(it)) }

    constraints {
        if (!hasOkioBom) libs.onLibrary("square-okio") { implementation(it) }
        if (!hasOkhttpBom) libs.onLibrary("square-okhttp") { implementation(it) }

        // TODO: With coil-bom:2.2.2 doesn't work as enforcedPlatform for Android and/or KMP
        libs.onLibrary("coil-bom") { implementation(it) }

        libs.onBundle("kotlinx") { implementation(it) }
        libs.onBundle("koin") { implementation(it) }
        libs.onBundle("common") { implementation(it) }
    }
}

@Suppress("SpellCheckingInspection", "LongParameterList")
private fun KotlinCommonCompilerOptions.setupKotlinOptions(
    project: Project,
    isTestTask: Boolean,
    isReleaseTask: Boolean,
    warningsAsErrors: Boolean,
    config: KotlinConfigSetup,
    optIns: List<String>,
) {
    val isCI by project.isCI()
    val isRelease by project.isRelease()
    val useKotlinDebug by project.useKotlinDebug()
    val releaseSettings = isCI || isRelease || isReleaseTask

    val libs = project.libsCatalog

    // https://kotlinlang.org/docs/compatibility-modes.html
    val kotlinLangVersion = config.getKotlinLangVersion(libs)
    kotlinLangVersion.let {
        languageVersion.set(it)
        apiVersion.set(it)
    }

    // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/jvm/extraHelp.out
    // https://github.com/JetBrains/kotlin/blob/master/compiler/testData/cli/js/jsExtraHelp.out
    freeCompilerArgs.addAll(
        "-Xcontext-receivers",
        "-Xklib-enable-signature-clash-checks",
    )
    freeCompilerArgs.addAll(config.getListOfOptIns(isTestTask, optIns).map { "-opt-in=$it" })

    val isLatestKotlinVersion = kotlinLangVersion == KotlinVersion.values().last()
    if (config.progressiveMode && isLatestKotlinVersion) {
        freeCompilerArgs.add("-progressive")
    }

    if (this is KotlinJvmCompilerOptions) {
        val javaLangTarget = libs.getJavaLangTarget(config)
        if (!javaLangTarget.isNullOrEmpty()) {
            jvmTarget.set(JvmTarget.fromTarget(javaLangTarget))
        }

        javaParameters.set(config.javaParameters)

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
        if (!warningsAsErrors && config.useExperimentalFastJarFs) {
            freeCompilerArgs.add("-Xuse-fast-jar-file-system")
        }

        // class mode provides lambdas arguments names
        val lambdaType = when {
            config.useIndyLambdas || isCI || releaseSettings -> "indy"
            else -> "class"
        }
        freeCompilerArgs.addAll(
            "-Xlambdas=$lambdaType",
            "-Xsam-conversions=$lambdaType",
        )

        // Remove utility bytecode, eliminating names/data leaks in release obfuscated code.
        // https://proandroiddev.com/kotlin-cleaning-java-bytecode-before-release-9567d4c63911
        // https://www.guardsquare.com/blog/eliminating-data-leaks-caused-by-kotlin-assertions
        if (releaseSettings && config.removeAssertionsInRelease) {
            freeCompilerArgs.addAll(
                "-Xno-call-assertions",
                "-Xno-param-assertions",
                "-Xno-receiver-assertions",
            )
        }
    }

    // https://kotlinlang.org/docs/whatsnew18.html#a-new-compiler-option-for-disabling-optimizations
    if (!releaseSettings && useKotlinDebug) {
        freeCompilerArgs.add("-Xdebug")
    }

    libs.onVersion(ALIAS_ANDROIDX_COMPOSE_COMPILER) { _ ->
        val p = "plugin:androidx.compose.compiler.plugins.kotlin"
        if (config.suppressKotlinComposeCompatCheck) {
            val kotlin = libs.version("kotlin")
            freeCompilerArgs.addAll("-P", "$p:suppressKotlinVersionCompatibilityCheck=$kotlin")
        }

        val isMaxDebug by project.isMaxDebugEnabled()
        val composeMetricsEnabled by project.areComposeMetricsEnabled()
        @Suppress("MaxLineLength", "ArgumentListWrapping", "ComplexCondition")
        if (isCI || isRelease || composeMetricsEnabled || isMaxDebug) {
            // https://chris.banes.dev/composable-metrics/
            // https://github.com/androidx/androidx/blob/androidx-main/compose/compiler/design/compiler-metrics.md#interpreting-compose-compiler-metrics
            val reportsDir = "${project.buildDir.absolutePath}/reports/compose"
            freeCompilerArgs.addAll(
                "-P",
                "$p:metricsDestination=$reportsDir",
                "-P",
                "$p:reportsDestination=$reportsDir",
            )

            // Convert the report to human-readable html.
            // https://patilshreyas.github.io/compose-report-to-html/
            // $ composeReport2Html -app LinenWallet -overallStatsReport app_primaryDebug-module.json -detailedStatsMetrics app_primaryDebug-composables.csv -composableMetrics app_primaryDebug-composables.txt -classMetrics app_primaryDebug-classes.txt -o htmlReportDebug
            // $ composeReport2Html -app LinenWallet -overallStatsReport app_primaryRelease-module.json -detailedStatsMetrics app_primaryRelease-composables.csv -composableMetrics app_primaryRelease-composables.txt -classMetrics app_primaryRelease-classes.txt -o htmlReportRelease
        }
    }

    config.configurator?.invoke(this)
}

internal fun VersionCatalog.getJavaLangTarget(config: KotlinConfigSetup?): String? {
    return config?.javaLangTarget ?: if (config?.setupJvmToolchain == true) {
        v("javaToolchain") ?: v("javaLangTarget")
    } else {
        v("javaLangTarget") ?: v("javaToolchain")
    }
}

private fun KotlinConfigSetup.getKotlinLangVersion(libs: VersionCatalog): KotlinVersion {
    val v = kotlinLangVersion ?: libs.version("kotlinLangVersion")
    return when {
        v.equals("last", ignoreCase = true) ||
            v.equals("latest", ignoreCase = true) ||
            v.equals("max", ignoreCase = true) ||
            v == "+"
        -> KotlinVersion.values().last()

        else -> KotlinVersion.fromVersion(v)
    }
}

private fun KotlinConfigSetup.getListOfOptIns(isTest: Boolean, optIns: List<String>): List<String> {
    val set = mutableSetOf(
        "kotlin.RequiresOptIn",
        "kotlin.contracts.ExperimentalContracts",
        "kotlin.experimental.ExperimentalObjCName",
        "kotlin.experimental.ExperimentalTypeInference",
    )
    set.addAll(this.optIns)
    set.addAll(optIns)
    if (setupCoroutines && (isTest || optInInternal)) {
        set.add("kotlinx.coroutines.DelicateCoroutinesApi")
        set.add("kotlinx.coroutines.ExperimentalCoroutinesApi")
        set.add("kotlinx.coroutines.InternalCoroutinesApi")
    }
    return set.toList()
}


internal val excludeAnnotations: ExternalModuleDependency.() -> Unit = {
    exclude(group = "org.jetbrains", module = "annotations")
}

internal const val KMP_PLUGIN_ID = "org.jetbrains.kotlin.multiplatform"
internal const val KAPT_PLUGIN_ID = "org.jetbrains.kotlin.kapt"
internal const val KSP_PLUGIN_ID = "com.google.devtools.ksp"

internal const val COROUTINES_DEPENDENCY = "org.jetbrains.kotlinx:kotlinx-coroutines-core"
