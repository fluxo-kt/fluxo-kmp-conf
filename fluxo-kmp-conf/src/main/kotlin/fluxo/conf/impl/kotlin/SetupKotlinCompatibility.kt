@file:Suppress("MagicNumber")

package fluxo.conf.impl.kotlin

import bundle
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.isTestRelated
import fluxo.log.e
import kotlin.KotlinVersion
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinCommonOptions
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion
import org.jetbrains.kotlin.gradle.plugin.sources.AbstractKotlinSourceSet

internal typealias KCompilation = KotlinCompilation<KotlinCommonOptions>

internal fun KotlinCommonOptions.setupKotlinCompatibility(
    conf: FluxoConfigurationExtensionImpl,
    isTest: Boolean,
    isExperimentalTest: Boolean,
) {
    val (lang, api) = conf.kotlinConfig
        .langAndApiVersions(isTest = isTest, latestSettings = isExperimentalTest)
    lang?.apply { languageVersion = version }
    api?.apply { apiVersion = version }
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

    languageSettings.apply {
        val (lang, api) = kc.langAndApiVersions(isTest = isTestSet)
        lang?.run { languageVersion = version }
        api?.run { apiVersion = version }

        if (kc.progressive && lang.isCurrentOrLater) {
            progressiveMode = true
        }

        (if (isTestSet) testOptIns else kc.optIns)
            .forEach(::optIn)
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
            return parseKotlinPluginVersion(versionString).also { v ->
                KOTLIN_PLUGIN_VERSION = v
                if (v >= FIRST_UNTABULATED_KOTLIN && !WARNED_KOTLIN_BEYOND_TABLE) {
                    WARNED_KOTLIN_BEYOND_TABLE = true
                    logger.warn(
                        "[fluxo-kmp-conf] Kotlin plugin $v is at or beyond the " +
                            "first untabulated minor ($FIRST_UNTABULATED_KOTLIN) " +
                            "in the JVM-target compatibility table at " +
                            "KotlinVersionTable.kt#toKotlinSupportedJvmMajorVersion. " +
                            "JVM target may be silently capped at the last " +
                            "tabulated value — extend the table.",
                    )
                }
            }
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


// region androidSourceSetLayout v2

/**
 * Detect the Kotlin androidSourceSetLayout v2
 *
 * [Kotlin 1.8](https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema)
 * added a new source set layout for Android projects.
 * It's the default since
 * [Kotlin 1.9](https://kotlinlang.org/docs/whatsnew19.html#new-android-source-set-layout-enabled-by-default)
 *
 * @see org.jetbrains.kotlin.gradle.plugin.PropertiesProvider.PropertyNames.KOTLIN_MPP_ANDROID_SOURCE_SET_LAYOUT_VERSION
 * @see bundle
 */
@Suppress("MaxLineLength")
internal fun Project.mppAndroidSourceSetLayoutVersion(version: KotlinVersion): Boolean {
    // https://kotlinlang.org/docs/whatsnew19.html#new-android-source-set-layout-enabled-by-default
    // https://kotlinlang.org/docs/whatsnew18.html#kotlinsourceset-naming-schema
    val layoutVersion = when {
        version >= KOTLIN_1_9 -> mppAndroidSourceSetLayoutVersionProp ?: 2
        version >= KOTLIN_1_8 -> mppAndroidSourceSetLayoutVersionProp ?: 1
        else -> 1
    }
    return layoutVersion == 2
}

private val Project.mppAndroidSourceSetLayoutVersionProp: Int?
    get() = extensions.extraProperties.properties["kotlin.mpp.androidSourceSetLayoutVersion"]
        ?.toString()?.toIntOrNull()

// endregion
