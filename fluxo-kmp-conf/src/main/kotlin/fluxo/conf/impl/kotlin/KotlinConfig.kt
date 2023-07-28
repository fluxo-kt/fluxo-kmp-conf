@file:Suppress("MagicNumber", "ReturnCount")

package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.hasRoomPlugin
import fluxo.conf.impl.l
import fluxo.conf.impl.w
import kotlin.KotlinVersion
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion

@Suppress("LongParameterList")
internal class KotlinConfig(
    // https://kotlinlang.org/docs/compatibility-modes.html
    /** Can't be lower than [api]! */
    val lang: KotlinLangVersion?,
    val api: KotlinLangVersion?,
    val tests: KotlinLangVersion?,
    val coreLibs: String,

    val jvmTarget: String?,
    val jvmTargetInt: Int,
    val jvmTestTarget: String?,
    val jvmToolchain: Boolean,

    val progressive: Boolean,
    val latestCompilation: Boolean,
    val warningsAsErrors: Boolean,
    val javaParameters: Boolean,
    val fastJarFs: Boolean,
    val useIndyLambdas: Boolean,
    val removeAssertionsInRelease: Boolean,
    val addStdlibDependency: Boolean,
    val setupKnownBoms: Boolean,

    val setupKsp: Boolean,
    val setupKapt: Boolean,
    val setupRoom: Boolean,
    val setupCompose: Boolean,
    val setupCoroutines: Boolean,
    val setupSerialization: Boolean,
    val optIns: Set<String>,
    val optInInternal: Boolean,
) {
    fun langAndApiVersions(
        isTest: Boolean,
        latestSettings: Boolean = false,
    ): Pair<KotlinLangVersion?, KotlinLangVersion?> {
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
            return lastKnownJvmTargetVersion(jvmToolchain)
        }
        if (isTest) {
            jvmTestTarget?.let { return it }
        }
        return jvmTarget
    }

    /** @see ANDROID_SAFE_JVM_TARGET */
    val useSafeAndroidOptions get() = jvmTargetInt > ANDROID_SAFE_JVM_TARGET
}

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FluxoConfigurationExtensionImpl.KotlinConfig(
    project: Project,
    k: KotlinProjectExtension,
): KotlinConfig {
    val context = context
    val pluginVersion = context.kotlinPluginVersion
    val coreLibs = kotlinCoreLibraries
        ?.takeIf { it.isNotBlank() && it != pluginVersion.toString() }
        ?: k.coreLibrariesVersion

    // Note: apiVersion can't be greater than languageVersion!
    var lang = kotlinLangVersion?.toKotlinLangVersion()
    var api = kotlinApiVersion?.toKotlinLangVersion()?.takeIf { it != lang }
    if (api != null && lang == null) {
        lang = api
        api = null
    }

    val jvmToolchain = setupJvmToolchain ?: false
    val jvmTarget = javaLangTarget?.toJvmTargetVersion(jvmToolchain)
    val jvmTargetInt = jvmTarget?.asJvmMajorVersion() ?: JRE_VERSION
    val javaParameters = jvmTargetInt >= JRE_1_8 && javaParameters ?: false

    val progressive = progressiveMode ?: true

    @Suppress("DEPRECATION")
    val canUseLatestSettings = progressive && pluginVersion >= KOTLIN_1_4
        && (lang == null || lang >= KotlinLangVersion.KOTLIN_1_4)

    var tests = kotlinTestsLangVersion?.toKotlinLangVersion()
    val latestTests = latestSettingsForTests != false
    if (tests == null && canUseLatestSettings && latestTests) {
        tests = LATEST_KOTLIN_LANG_VERSION
    }
    tests?.run { tests = takeIf { it != lang } }


    // The test target can't be lower than the main target version!
    var jvmTests = javaTestsLangTarget?.toJvmTargetVersion(jvmToolchain)?.takeIf { v ->
        if (jvmTarget != null) v != jvmTarget else v.asJvmMajorVersion() != jvmTargetInt
    }
    if (jvmTests == null && canUseLatestSettings && latestTests) {
        jvmTests = lastKnownJvmTargetVersion(jvmToolchain)
    }
    jvmTests?.run { jvmTests = takeIf { it != jvmTarget } }


    // Experimental test compilation with the latest Kotlin settings.
    // Don't try it for sources with old compatibility settings.
    val latestCompilation = canUseLatestSettings
        && !context.isInCompositeBuild
        && !context.testsDisabled
        && experimentalLatestCompilation != false

    val setupCoroutines = setupCoroutines ?: true
    val optInInternal = optInInternal ?: false
    val optIns = prepareOptIns(
        optIns = DEFAULT_OPT_INS + optIns,
        setupCoroutines = setupCoroutines,
        optInInternal = optInInternal,
    )

    val setupRoom = setupRoom == true || project.hasRoomPlugin
    val kc = KotlinConfig(
        lang = lang,
        api = api,
        tests = tests,
        coreLibs = coreLibs,

        jvmTarget = jvmTarget,
        jvmTargetInt = jvmTargetInt,
        jvmTestTarget = jvmTests,
        jvmToolchain = jvmToolchain,

        progressive = progressive,
        latestCompilation = latestCompilation,
        warningsAsErrors = warningsAsErrors ?: false,
        javaParameters = javaParameters,
        fastJarFs = useExperimentalFastJarFs ?: true,
        useIndyLambdas = useIndyLambdas ?: true,
        removeAssertionsInRelease = removeAssertionsInRelease ?: true,
        addStdlibDependency = addStdlibDependency,
        setupKnownBoms = setupKnownBoms,

        setupKsp = setupKsp == true || setupRoom || project.hasKsp,
        setupKapt = setupKapt == true || project.hasKapt,
        setupRoom = setupRoom,
        setupCompose = enableCompose == true || project.hasKmpCompose,
        setupCoroutines = setupCoroutines,
        setupSerialization = setupKotlinXSerialization,
        optIns = optIns,
        optInInternal = optInInternal,
    )
    project.logger.logKotlinProjectCompatibility(kc, pluginVersion)
    return kc
}

@Suppress("CyclomaticComplexMethod")
private fun Logger.logKotlinProjectCompatibility(
    kc: KotlinConfig,
    pluginVersion: KotlinVersion,
) {
    val msg = buildString(capacity = 64) {
        append("compatibility: Kotlin ")

        val pv = pluginVersion.toString()
        append(kc.lang?.version ?: pv)

        val ka = kc.api?.version
        val kt = kc.tests?.version
        val kl = kc.coreLibs.takeIf { it != pv }
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
            append(jv ?: kc.jvmTargetInt.asJvmTargetVersion())

            if (jt != null) {
                append(" (")
                append("tests $jt")
                append(')')
            }
        }
    }
    l(msg)

    if (kc.jvmToolchain) {
        w(
            "JVM toolchain setup is enabled! \n" +
                "Note that it can slow down the build and currently disables granular JVM target" +
                "configuration for different project targets, sources, compilations and tasks!",
        )
    }
}
