@file:Suppress("MagicNumber")

package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.isTestRelated
import fluxo.log.e
import kotlin.KotlinVersion
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet

internal typealias KCompilation = KotlinCompilation<*>

internal fun KotlinCommonCompilerOptions.setupKotlinCompatibility(
    conf: FluxoConfigurationExtensionImpl,
    isTest: Boolean,
    isExperimentalTest: Boolean,
) {
    val kc = conf.kotlinConfig
    val versions = kc.langAndApiVersions(isTest = isTest, latestSettings = isExperimentalTest)
    // Main compilations take the module's defaults (`setupModuleKotlinOptions`).
    if (versions == kc.langAndApiVersions(isTest = false)) return
    val (lang, api) = versions
    lang?.let { languageVersion.convention(it) }
    api?.let { apiVersion.convention(it) }
    progressiveMode.convention((kc.progressive || isExperimentalTest) && lang.isCurrentOrLater)
}

internal fun KotlinProjectExtension.setupSourceSetsKotlinCompatibility(
    project: Project,
    kc: KotlinConfig,
    disableTests: Boolean = false,
) {
    configureSourceSets(kc, disableTests)
    if (this is KotlinMultiplatformExtension) setupSharedSourceSetsCoroutinesOptIns(project, kc)
}

/**
 * [COROUTINES_OPT_INS] for the IDE in shared source sets with no compiler of their own
 * (`commonTest`, `nativeTest`, …; main ones only with [KotlinConfig.optInInternal]): compile
 * tasks get them from [coroutinesOptIns], only when coroutines are on their classpath. A
 * compilation's own source set must not get them here: its `languageSettings` are that
 * compilation's options, so they would reach the compiler unconditionally ("is unresolved",
 * fatal under warnings-as-errors).
 *
 * Deferred to `afterEvaluate` because which source sets are some compilation's own is known only
 * once every compilation exists: KGP creates a target's source sets before registering its
 * compilations, so `KotlinSourceSet` reports none while `sourceSets.configureEach` runs.
 * Removable once KGP exposes that on the source set when it is created. Single-target modules
 * need none of this: every source set there is a compilation's own.
 */
private fun KotlinMultiplatformExtension.setupSharedSourceSetsCoroutinesOptIns(
    project: Project,
    kc: KotlinConfig,
) {
    if (!kc.setupCoroutines) return
    project.afterEvaluate {
        val compiled = targets.flatMapTo(HashSet()) { target ->
            target.compilations.map { it.defaultSourceSet }
        }
        sourceSets.forEach { set ->
            if (set !in compiled && (kc.optInInternal || set.isTestRelated())) {
                COROUTINES_OPT_INS.forEach(set.languageSettings::optIn)
            }
        }
    }
}

private fun KotlinProjectExtension.configureSourceSets(kc: KotlinConfig, disableTests: Boolean) =
    sourceSets.configureEach {
        val isTestSet = isTestRelated()

        // Test compilations should be turned off from targets.
        if (DISABLE_COMPILATIONS_FROM_SOURCE_SETS && isTestSet && disableTests) {
            @Suppress("UnsafeCast")
            (this as? AbstractKotlinSourceSet)?.compilations?.forEach { it.disableCompilation() }
        }

        // A `languageSettings` setter writes its compilation's options with `set()`, which would
        // override the consumer's module-level `compilerOptions`. Main values come from the
        // module's defaults instead (KGP also passes them to shared source sets without a
        // compilation), so only the test language version is written here, which shared test
        // source sets need for the IDE.
        if (isTestSet) {
            kc.tests?.let {
                languageSettings.languageVersion = it.version
                languageSettings.apiVersion = it.version
            }
        }
    }

private const val DISABLE_COMPILATIONS_FROM_SOURCE_SETS = false


// region Kotlin versions and compatibility

/**
 * Gets the current Kotlin plugin version.
 *
 * Side effect: updates the [KOTLIN_PLUGIN_VERSION] value.
 *
 * @see getKotlinPluginVersion
 * @see kotlin.KotlinVersion
 */
internal fun Logger.kotlinPluginVersion(): KotlinVersion {
    val logger = this
    try {
        getKotlinPluginVersion(logger).let { versionString ->
            KOTLIN_PLUGIN_VERSION_STRING = versionString
            return parseKotlinPluginVersion(versionString).also { KOTLIN_PLUGIN_VERSION = it }
        }
    } catch (e: Throwable) {
        logger.e("Failed to get Kotlin plugin version: $e", e)
    }
    return KOTLIN_PLUGIN_VERSION
}

internal fun String.toKotlinLangVersion(): KotlinLangVersion? {
    return when {
        isBlank() -> null

        equals("last", ignoreCase = true) ||
            equals("latest", ignoreCase = true) ||
            equals("max", ignoreCase = true) ||
            equals("+")
        -> LATEST_KOTLIN_LANG_VERSION

        equals("current", ignoreCase = true) || isEmpty()
        -> KOTLIN_LANG_VERSION

        else -> KotlinLangVersion.fromVersion(this)
    }
}

internal val KotlinLangVersion?.isCurrentOrLater: Boolean
    get() = this == null || this >= KOTLIN_LANG_VERSION

/** @see org.jetbrains.kotlin.gradle.dsl.KotlinVersion */
internal val LATEST_KOTLIN_LANG_VERSION = KotlinLangVersion.values().last()

/** @see org.jetbrains.kotlin.gradle.dsl.KotlinVersion */
private val KOTLIN_LANG_VERSION = KotlinLangVersion.DEFAULT

// endregion


