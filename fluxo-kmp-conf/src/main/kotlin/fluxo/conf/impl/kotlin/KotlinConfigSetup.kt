package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.hasRoomPlugin
import fluxo.log.l
import fluxo.log.w
import noManualHierarchy
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion

@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun FluxoConfigurationExtensionImpl.KotlinConfig(
    project: Project,
    k: KotlinProjectExtension,
): KotlinConfig {
    val context = ctx
    val pluginVersion = context.kotlinPluginVersion
    val coreLibs = kotlinCoreLibraries
        ?.takeIf { it.isNotBlank() && it != "0" && it != pluginVersion.toString() }
        ?: k.coreLibrariesVersion

    // Note: apiVersion can't be greater than languageVersion!
    var lang = kotlinLangVersion?.toKotlinLangVersion()
    var api = kotlinApiVersion?.toKotlinLangVersion()?.takeIf { it != lang }
    if (api != null && lang == null) {
        lang = api
        api = null
    }
    if (api != null && lang != null && api > lang) {
        project.logger.w(
            "Kotlin API version is downgraded from $api to $lang" +
                ", as it can't be greater than the language version!",
        )
        api = null
    }

    val k2 = pluginVersion >= KOTLIN_2_0 || try {
        lang != null && lang > KotlinLangVersion.KOTLIN_1_9
    } catch (_: Throwable) {
        false
    }

    // TODO: Detect if JVM toolchains are already enabled in the project.
    val jvmToolchain = setupJvmToolchain
    var jvmTargetInt = jvmTarget?.toJvmMajorVersion(jvmToolchain) ?: 0
    val jvmTarget: String?
    if (jvmTargetInt <= 0) {
        jvmTargetInt = JRE_VERSION.toKotlinSupportedJvmMajorVersion()
        jvmTarget = null
    } else {
        jvmTarget = jvmTargetInt.asJvmTargetVersion()
    }
    val javaParameters = jvmTargetInt >= JRE_1_8 &&
        javaParameters ?: false &&
        !isApplication

    // `jdk-release` requires Kotlin 1.7.0 or newer and JDK 9 or newer.
    // Also, no sense to use it with the JVM toolchain.
    //
    // TODO: Auto detect if `-Xjdk-release` actually can be used.
    //  Fail only for release builds if not, warn otherwise.
    //  If there's no `ct.sym` file in JDK but `-Xjdk-release` is used,
    //  the compiler will stop with an error._
    //  https://youtrack.jetbrains.com/issue/KT-29974#focus=Comments-27-9458958.0-0
    val useJdkRelease = useJdkRelease && !jvmToolchain &&
        JRE_VERSION >= JRE_1_9 &&
        pluginVersion >= KOTLIN_1_7

    val progressive = progressiveMode ?: true

    val canUseLatestSettings = progressive &&
        pluginVersion >= KOTLIN_1_4 &&
        @Suppress("DEPRECATION")
        (lang == null || lang >= KotlinLangVersion.KOTLIN_1_4)


    var tests = kotlinTestsLangVersion?.toKotlinLangVersion()
    val latestTests = latestSettingsForTests == true
    if (tests == null && canUseLatestSettings && latestTests) {
        tests = LATEST_KOTLIN_LANG_VERSION
    }
    tests?.run { tests = takeIf { it != lang } }

    // The tests JVM target can't be lower than the main target version!
    var jvmTestsInt = javaTestsLangTarget?.toJvmMajorVersion(jvmToolchain) ?: 0
    if (jvmTestsInt <= 0 && canUseLatestSettings && latestTests) {
        jvmTestsInt = lastSupportedJvmMajorVersion(jvmToolchain)
    }
    val jvmTests = when {
        jvmTestsInt <= jvmTargetInt -> null
        else -> jvmTestsInt.asJvmTargetVersion()
    }

    // As of Kotlin 1.9.20,
    // none of the source sets can depend on the compilation default source sets.
    val allowManualHierarchy = pluginVersion < KOTLIN_1_9_20 &&
        !project.noManualHierarchy() &&
        setupLegacyKotlinHierarchy

    // Experimental test compilation with the latest Kotlin settings.
    // Don't try it for sources with old compatibility settings.
    // TODO: Add env flag for dynamic switch-on when needed
    //  (and always enable by a task name if called directly)
    val latestCompilation = canUseLatestSettings &&
        !context.testsDisabled &&
        experimentalLatestCompilation == true

    val setupCoroutines = setupCoroutines ?: true
    val optInInternal = optInInternal ?: false
    val optIns = prepareOptIns(
        optIns = DEFAULT_OPT_INS + optIns,
        setupCoroutines = setupCoroutines,
        optInInternal = optInInternal,
    )

    val setupRoom = setupRoom == true || project.hasRoomPlugin

    val hasKotlinCompose = project.hasKotlinCompose
    val setupCompose = enableCompose == true || project.hasKmpCompose || hasKotlinCompose
    val useKotlinCompose = hasKotlinCompose || setupCompose && pluginVersion >= KOTLIN_2_0

    val kc = KotlinConfig(
        lang = lang,
        api = api,
        tests = tests,
        coreLibs = coreLibs,
        k2 = k2,

        jvmTarget = jvmTarget,
        jvmTargetInt = jvmTargetInt,
        jvmTestTarget = jvmTests,
        jvmToolchain = jvmToolchain,
        useJdkRelease = useJdkRelease,

        progressive = progressive,
        latestCompilation = latestCompilation,
        warningsAsErrors = allWarningsAsErrors ?: false,
        javaParameters = javaParameters,
        fastJarFs = useExperimentalFastJarFs ?: true,
        useIndyLambdas = useIndyLambdas ?: true,
        removeAssertionsInRelease = removeAssertionsInRelease ?: true,
        addStdlibDependency = addStdlibDependency,
        setupKnownBoms = setupKnownBoms,

        setupKsp = setupKsp == true || setupRoom || project.hasKsp,
        setupKapt = setupKapt == true || project.hasKapt,
        setupRoom = setupRoom,
        setupCompose = setupCompose,
        useKotlinCompose = useKotlinCompose,
        setupCoroutines = setupCoroutines,
        setupSerialization = setupKotlinXSerialization,
        optIns = optIns,
        optInInternal = optInInternal,

        allowManualHierarchy = allowManualHierarchy,
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
                "Note that it's rarely beneficial because of inefficient resource usage," +
                "compiler bugs, reduced performance and outdated javadoc," +
                "without significant advantages for the most JVM projects. \n" +
                "Atm, in Fluxo Conf it also disables granular JVM target configuration" +
                "for different project targets, sources, compilations and tasks! \n" +
                "See https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/",
        )
    }
}
