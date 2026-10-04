package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.hasRoomPlugin
import fluxo.conf.impl.envOrPropFlagValue
import fluxo.log.l
import fluxo.log.logDecision
import fluxo.log.w
import kotlin.math.min
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

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

    // Fail fast on a kotlin-stdlib newer than the compiler: otherwise the resulting
    // version-mismatch warning only becomes fatal under allWarningsAsErrors on CI/release,
    // surfacing far from its `kotlinCoreLibraries` vs `kotlin` root cause.
    kotlinStdlibSkewError(pluginVersion, coreLibs)?.let { msg ->
        require(project.envOrPropFlagValue(ALLOW_KOTLIN_STDLIB_SKEW_PROP)) { msg }
        project.logger.w("$msg [allowed via -P$ALLOW_KOTLIN_STDLIB_SKEW_PROP]")
    }

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

    // TODO: Detect if JVM toolchains are already enabled in the project.
    val jvmToolchain = setupJvmToolchain
    val explicitJvmTarget = jvmTarget?.toJvmMajorVersion(jvmToolchain)?.takeIf { it > 0 }
    val jvmTargetInt = explicitJvmTarget ?: defaultJvmTarget(project)
    val jvmTarget = jvmTargetInt.asJvmTargetVersion()
    val javaParameters = jvmTargetInt >= JRE_1_8 &&
        javaParameters ?: false &&
        !isApplication

    val progressive = progressiveMode ?: true

    // No Kotlin version gate: every supported Kotlin (2.1+) has the latest settings.
    val canUseLatestSettings = progressive


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
    val useKotlinCompose = hasKotlinCompose || setupCompose

    val kc = KotlinConfig(
        lang = lang,
        api = api,
        tests = tests,
        coreLibs = coreLibs,

        jvmTarget = jvmTarget,
        jvmTargetInt = jvmTargetInt,
        jvmTargetExplicit = explicitJvmTarget != null,
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

        val jt = kc.jvmTestTarget
        run {
            append(", JVM ")
            if (kc.jvmToolchain) append("toolchain ")
            append(kc.jvmTarget)

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
                "Note that it's rarely beneficial because of inefficient resource usage, " +
                "compiler bugs, reduced performance and outdated javadoc, " +
                "without significant advantages for the most JVM projects. \n" +
                "Atm, in Fluxo Conf it also disables granular JVM target configuration " +
                "for different project targets, sources, compilations and tasks! \n" +
                "See https://jakewharton.com/gradle-toolchains-are-rarely-a-good-idea/",
        )
    }
}

/**
 * The JVM target of a module that sets none (R10). It must not follow whichever JDK happens to run
 * the build: that made bytecode machine-dependent and sank v0.15.0's first release tag.
 * - Libraries get 17, the oldest JDK Gradle 9 runs on, so any consumer can load them.
 * - Applications ship with their own runtime, so they get the newest target the JDK running Gradle
 *   and Kotlin allow. With `gradle/gradle-daemon-jvm.properties` that JDK is the criteria JDK:
 *   Gradle starts the daemon on it over `JAVA_HOME` and `org.gradle.java.home` (Gradle 9.8,
 *   measured 2026-10-04), so the project's pin is honoured with no file read.
 * A consumer's own Java toolchain is not consulted: `fkcSetup*()` applies the Kotlin plugin, so a
 * toolchain is set after this runs. A toolchain below the derived target fails loudly in the
 * compiler ("invalid target release"), and setting `jvmTarget` fixes it.
 * Android ignores this value and keeps AGP's default ([KotlinConfig.jvmTargetExplicit]).
 */
private fun FluxoConfigurationExtensionImpl.defaultJvmTarget(project: Project): Int {
    val target: Int
    val reason: String
    if (isApplication) {
        target = min(JRE_VERSION, KOTLIN_MAX_JVM_TARGET)
        reason = "application: the newest that JDK $JRE_VERSION running Gradle and " +
            "Kotlin $KOTLIN_PLUGIN_VERSION_STRING allow"
    } else {
        target = LIBRARY_JVM_TARGET
        reason = "library default, loadable on every JDK Gradle 9 runs on"
    }
    ctx.logDecision(
        project,
        setting = "jvmTarget",
        value = target,
        reason = reason,
        howToChange = "set jvmTarget in fkcSetup* or the version catalog",
    )
    return target
}

/** See [defaultJvmTarget]. */
private const val LIBRARY_JVM_TARGET = JRE_17
