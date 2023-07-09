@file:Suppress("MagicNumber", "ReturnCount")

package fluxo.conf.impl.kotlin

import bundle
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.e
import fluxo.conf.impl.l
import hasKapt
import hasKsp
import kotlin.math.max
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.plugins.PluginAware
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.getKotlinPluginVersion


// region KotlinCompatibility

@Suppress("LongParameterList")
internal class KotlinConfig(
    // https://kotlinlang.org/docs/compatibility-modes.html
    val lang: KotlinVersion?,
    val api: KotlinVersion?,
    val tests: KotlinVersion?,
    val coreLibs: String?,

    val jvmTarget: String?,
    val jvmTestTarget: String?,
    val jvmToolchain: Boolean,

    val progressive: Boolean,
    val latestCompilation: Boolean,
    val warningsAsErrors: Boolean,
    val javaParameters: Boolean,
    val useExperimentalFastJarFs: Boolean,
    val useIndyLambdas: Boolean,
    val removeAssertionsInRelease: Boolean,
    val suppressKotlinComposeCompatCheck: Boolean,
    val addStdlibDependency: Boolean,
    val setupKnownBoms: Boolean,

    val setupKsp: Boolean,
    val setupKapt: Boolean,
    val setupCoroutines: Boolean,
    val optIns: Set<String>,
    val optInInternal: Boolean,
) {
    fun langAndApiVersions(
        isTest: Boolean,
        latestSettings: Boolean = false,
    ): Pair<KotlinVersion?, KotlinVersion?> {
        if (latestSettings) {
            LATEST_KOTLIN_LANG_VERSION.let { return it to it }
        }
        if (isTest) {
            tests?.let { return it to it }
        }
        return lang.let { it to (api ?: it) }
    }

    fun jvmTargetVersion(
        isTest: Boolean,
        latestSettings: Boolean = false,
    ): String? {
        if (latestSettings) {
            return lastJdkVersion(jvmToolchain)
        }
        if (isTest) {
            jvmTestTarget?.let { return it }
        }
        return jvmTarget
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FluxoConfigurationExtensionImpl.KotlinConfig(
    project: Project,
): KotlinConfig {
    val context = context
    val pluginVersion = context.kotlinPluginVersion
    val coreLibs = kotlinCoreLibraries
        ?.takeIf { it.isNotBlank() && it != pluginVersion.toString() }

    // Note: apiVersion can't be greater than languageVersion!
    var lang = kotlinLangVersion?.toKotlinLangVersion()
    var api = kotlinApiVersion?.toKotlinLangVersion()?.takeIf { it != lang }
    if (api != null && lang == null) {
        lang = api
        api = null
    }

    val jvmToolchain = setupJvmToolchain ?: false
    val jvmTarget = javaLangTarget?.toJvmTargetVersion(jvmToolchain)


    val progressive = progressiveMode ?: true

    @Suppress("DEPRECATION")
    val canUseLatestSettings = progressive && pluginVersion >= KOTLIN_1_4
        && (lang == null || lang >= KotlinVersion.KOTLIN_1_4)

    var tests = kotlinTestsLangVersion?.toKotlinLangVersion()
    val latestTests = latestSettingsForTests != false
    if (tests == null && canUseLatestSettings && latestTests) {
        tests = LATEST_KOTLIN_LANG_VERSION
    }
    tests?.run { tests = takeIf { it != lang } }


    var jvmTests = javaTestsLangTarget?.toJvmTargetVersion(jvmToolchain)
        ?.takeIf { it != jvmTarget }
    if (jvmTests == null && canUseLatestSettings && latestTests) {
        jvmTests = lastJdkVersion(jvmToolchain)
    }
    jvmTests?.run { jvmTests = takeIf { it != jvmTarget } }


    // Experimental test compilation with the latest Kotlin settings.
    // Don't try for sources with old compatibility settings.
    val latestCompilation = canUseLatestSettings && !context.isInCompositeBuild
        && !context.testsDisabled && experimentalLatestCompilation != false

    val setupCoroutines = setupCoroutines ?: true
    val optInInternal = optInInternal ?: false
    val optIns = prepareOptIns(
        optIns = DEFAULT_OPT_INS + optIns,
        setupCoroutines = setupCoroutines,
        optInInternal = optInInternal,
    )

    val kc = KotlinConfig(
        lang = lang,
        api = api,
        tests = tests,
        coreLibs = coreLibs,

        jvmTarget = jvmTarget,
        jvmTestTarget = jvmTests,
        jvmToolchain = jvmToolchain,

        progressive = progressive,
        latestCompilation = latestCompilation,
        warningsAsErrors = warningsAsErrors ?: false,
        javaParameters = javaParameters ?: false,
        useExperimentalFastJarFs = useExperimentalFastJarFs ?: true,
        useIndyLambdas = useIndyLambdas ?: true,
        removeAssertionsInRelease = removeAssertionsInRelease ?: true,
        suppressKotlinComposeCompatCheck = suppressKotlinComposeCompatibilityCheck ?: false,
        addStdlibDependency = addStdlibDependency ?: false,
        setupKnownBoms = setupKnownBoms ?: false,

        setupKsp = setupKsp == true || project.hasKsp,
        setupKapt = setupKapt == true || project.hasKapt,
        setupCoroutines = setupCoroutines,
        optIns = optIns,
        optInInternal = optInInternal,
    )
    project.logger.logKotlinProjectCompatibility(kc, pluginVersion)
    return kc
}

internal val PluginAware.hasKsp: Boolean get() = plugins.hasPlugin("com.google.devtools.ksp")

@Suppress("CyclomaticComplexMethod")
private fun Logger.logKotlinProjectCompatibility(
    kc: KotlinConfig,
    pluginVersion: kotlin.KotlinVersion,
) {
    val msg = buildString(capacity = 64) {
        append("compatibility: Kotlin ")

        append(kc.lang?.version ?: pluginVersion)

        val ka = kc.api?.version
        val kt = kc.tests?.version
        val kl = kc.coreLibs
        if (ka != null || kt != null || kl != null) {
            append(" (")
            var first = true

            if (ka != null) {
                first = false
                append("API $ka")
            }
            if (kt != null) {
                if (!first) append(", ")
                first = false
                append("tests $kt")
            }
            if (kl != null) {
                if (!first) append(", ")
                append("libs $kl")
            }

            append(')')
        }

        val jv = kc.jvmTarget
        val jt = kc.jvmTestTarget
        if (jv != null || jt != null) {
            append(", JVM ")
            if (kc.jvmToolchain) append("toolchain ")
            append(jv ?: JDK_VERSION.asJvmTargetVersion())

            if (jt != null) {
                append(" (")
                append("tests $jt")
                append(')')
            }
        }
    }
    l(msg)
}

// endregion


// region Kotlin & JVM versions

/**
 * Gets the current Kotlin plugin version.
 *
 * @see getKotlinPluginVersion
 * @see kotlin.KotlinVersion
 */
internal fun Logger.kotlinPluginVersion(): kotlin.KotlinVersion {
    val logger = this
    try {
        getKotlinPluginVersion(logger).let { versionString ->
            val baseVersion = versionString.split("-", limit = 2)[0]
            val parts = baseVersion.split(".")
            return KotlinVersion(
                major = parts[0].toInt(),
                minor = parts[1].toInt(),
                patch = parts.getOrNull(2)?.toInt() ?: 0,
            )
        }
    } catch (e: Throwable) {
        logger.e("Failed to get Kotlin plugin version: $e", e)
    }
    return kotlin.KotlinVersion.CURRENT
}

private fun String.toKotlinLangVersion(): KotlinVersion? {
    return when {
        isBlank() -> null

        equals("last", ignoreCase = true) ||
            equals("latest", ignoreCase = true) ||
            equals("max", ignoreCase = true) ||
            equals("+")
        -> LATEST_KOTLIN_LANG_VERSION

        equals("current", ignoreCase = true) || isEmpty()
        -> KOTLIN_LANG_VERSION

        else -> KotlinVersion.fromVersion(this)
    }
}

internal val KotlinVersion?.isCurrentOrLater: Boolean
    get() = this == null || this >= KOTLIN_LANG_VERSION

/** @see org.jetbrains.kotlin.gradle.dsl.KotlinVersion */
private val LATEST_KOTLIN_LANG_VERSION = KotlinVersion.values().last()

/** @see org.jetbrains.kotlin.gradle.dsl.KotlinVersion */
private val KOTLIN_LANG_VERSION = try {
    KotlinVersion.DEFAULT
} catch (_: NoSuchMethodError) {
    val v = kotlin.KotlinVersion.CURRENT
    KotlinVersion.fromVersion("${v.major}.${v.minor}")
}


internal val KOTLIN_1_4 = KotlinVersion(1, 4)

internal val KOTLIN_1_8 = KotlinVersion(1, 8)

internal val KOTLIN_1_8_20 = KotlinVersion(1, 8, 20)

internal val KOTLIN_1_9 = KotlinVersion(1, 9)


// https://www.oracle.com/java/technologies/downloads/
private const val LTS_JDK_VERSION = 17

private val JDK_VERSION: Int = run {
    try {
        // For 9+
        Runtime.version().version().first()
    } catch (_: Throwable) {
        System.getProperty("java.version").asJvmMajorVersion()
    }
}

private fun lastJdkVersion(setupToolchain: Boolean = false): String {
    return (if (setupToolchain) max(JDK_VERSION, LTS_JDK_VERSION) else JDK_VERSION)
        .asJvmTargetVersion()
}

private fun Int.asJvmTargetVersion(): String = if (this >= 9) toString() else "1.$this"

private fun String.toJvmTargetVersion(setupToolchain: Boolean = false): String? {
    return when {
        isBlank() -> null

        equals("last", ignoreCase = true) ||
            equals("latest", ignoreCase = true) ||
            equals("max", ignoreCase = true) ||
            equals("+")
        -> lastJdkVersion(setupToolchain)

        equals("current", ignoreCase = true) || isEmpty()
        -> JDK_VERSION.asJvmTargetVersion()

        else -> this
    }
}

// 7 for 1.7, 8 for 1.8.0_211, 9 for 9.0.1.
internal fun String.asJvmMajorVersion(): Int =
    removePrefix("1.").takeWhile { it.isDigit() }.toInt()

// endregion


// region androidSourceSetLayout v2

/**
 * Detect the androidSourceSetLayout v2
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
internal fun Project.mppAndroidSourceSetLayoutVersion(version: kotlin.KotlinVersion): Boolean {
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
