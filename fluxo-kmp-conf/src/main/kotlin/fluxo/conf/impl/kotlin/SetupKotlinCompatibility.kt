@file:Suppress("MagicNumber")

package fluxo.conf.impl.kotlin

import bundle
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.e
import fluxo.conf.impl.isTestRelated
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

internal fun KCompilation.setupKotlinCompatibility(
    conf: FluxoConfigurationExtensionImpl,
    isTest: Boolean,
    isExperimentalTest: Boolean,
) {
    val kc = conf.context.kotlinConfig
    kotlinOptions.apply {
        val (lang, api) = kc
            .langAndApiVersions(isTest = isTest, latestSettings = isExperimentalTest)
        lang?.run { languageVersion = version }
        api?.run { apiVersion = version }

        if ((kc.progressive || isExperimentalTest) && lang.isCurrentOrLater) {
            freeCompilerArgs += "-progressive"
        }
    }
}

internal fun KotlinProjectExtension.setupSourceSetsKotlinCompatibility(
    kc: KotlinConfig,
    testOptIns: Set<String> = kc.prepareTestOptIns(),
    disableTests: Boolean = false,
) = sourceSets.all {
    val isTestSet = isTestRelated()

    // Test compilations should be turned off from targets
    if (DISABLE_COMPILATIONS_FROM_SOURCE_SETS && isTestSet && disableTests) {
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

private val KOTLIN_1_3_30 = KotlinVersion(1, 4, 30)

internal val KOTLIN_1_4 = KotlinVersion(1, 4)

private val KOTLIN_1_6 = KotlinVersion(1, 6)

internal val KOTLIN_1_7 = KotlinVersion(1, 8)

internal val KOTLIN_1_8 = KotlinVersion(1, 8)

internal val KOTLIN_1_8_20 = KotlinVersion(1, 8, 20)

internal val KOTLIN_1_9 = KotlinVersion(1, 9)

internal val KOTLIN_1_9_20 = KotlinVersion(1, 9, 20)


@Volatile
private var KOTLIN_PLUGIN_VERSION: KotlinVersion = KotlinVersion.CURRENT

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
            val baseVersion = versionString.split("-", limit = 2)[0]
            val parts = baseVersion.split(".")
            return KotlinVersion(
                major = parts[0].toInt(),
                minor = parts[1].toInt(),
                patch = parts.getOrNull(2)?.toInt() ?: 0,
            ).also { KOTLIN_PLUGIN_VERSION = it }
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

private val KotlinLangVersion?.isCurrentOrLater: Boolean
    get() = this == null || this >= KOTLIN_LANG_VERSION

/** @see org.jetbrains.kotlin.gradle.dsl.KotlinVersion */
internal val LATEST_KOTLIN_LANG_VERSION = KotlinLangVersion.values().last()

/** @see org.jetbrains.kotlin.gradle.dsl.KotlinVersion */
private val KOTLIN_LANG_VERSION = try {
    KotlinLangVersion.DEFAULT
} catch (_: NoSuchMethodError) {
    val v = KotlinVersion.CURRENT
    KotlinLangVersion.fromVersion("${v.major}.${v.minor}")
}

internal fun lastKnownJvmTargetVersion(setupToolchain: Boolean): String {
    var version = lastKnownJdkVersion(setupToolchain)

    // Align with the current Kotlin plugin supported JVM targets
    if (version > 8) {
        // 1.3.30 added support for 9..12.
        // https://blog.jetbrains.com/kotlin/2019/04/kotlin-1-3-30-released/#SpecifyingJVMbytecodetargets9%E2%80%9312
        // 1.4.0 supports also 13..14
        // https://stackoverflow.com/a/64331184/1816338
        // 1.6.0 added support for 17
        // https://kotlinlang.org/docs/whatsnew16.html#kotlin-jvm
        // 1.8.0 added support for 19
        // https://kotlinlang.org/docs/whatsnew18.html#kotlin-jvm
        // 1.9.0 added support for 20
        // https://kotlinlang.org/docs/whatsnew19.html#kotlin-jvm
        val pluginVersion = KOTLIN_PLUGIN_VERSION
        val maxSupportedTarget = when {
            pluginVersion >= KOTLIN_1_9 -> 20
            pluginVersion >= KOTLIN_1_8 -> 19
            pluginVersion >= KOTLIN_1_6 -> 17
            pluginVersion >= KOTLIN_1_4 -> 14
            pluginVersion >= KOTLIN_1_3_30 -> 12
            else -> 8
        }
        if (version > maxSupportedTarget) {
            version = maxSupportedTarget
        }
    }

    return version.asJvmTargetVersion()
}

internal fun String.toJvmTargetVersion(setupToolchain: Boolean = false): String? {
    return when {
        isBlank() -> null

        equals("last", ignoreCase = true) ||
            equals("latest", ignoreCase = true) ||
            equals("max", ignoreCase = true) ||
            equals("+")
        -> lastKnownJvmTargetVersion(setupToolchain)

        equals("current", ignoreCase = true) || isEmpty()
        -> JRE_VERSION.asJvmTargetVersion()

        else -> this
    }
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
