@file:Suppress("MagicNumber")

package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.isTestRelated
import fluxo.log.e
import kotlin.KotlinVersion
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonCompilerOptions
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
    kc: KotlinConfig,
    testOptIns: Set<String> = kc.prepareTestOptIns(),
    disableTests: Boolean = false,
) = sourceSets.configureEach {
    val isTestSet = isTestRelated()

    // Test compilations should be turned off from targets.
    if (DISABLE_COMPILATIONS_FROM_SOURCE_SETS && isTestSet && disableTests) {
        @Suppress("UnsafeCast")
        (this as? AbstractKotlinSourceSet)?.compilations?.forEach { it.disableCompilation() }
    }

    // A `languageSettings` setter writes its compilation's options with `set()`, which would
    // override the consumer's module-level `compilerOptions`. Main values come from the module's
    // defaults instead (KGP also passes them to shared source sets without a compilation), so
    // only the test-only settings are written here, which shared test source sets need for the
    // IDE.
    if (isTestSet) {
        languageSettings.apply {
            kc.tests?.let {
                languageVersion = it.version
                apiVersion = it.version
            }
            (testOptIns - kc.optIns).forEach(::optIn)
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
private val KOTLIN_LANG_VERSION = try {
    KotlinLangVersion.DEFAULT
} catch (_: NoSuchMethodError) {
    val v = KOTLIN_PLUGIN_VERSION
    KotlinLangVersion.fromVersion("${v.major}.${v.minor}")
}

// endregion


