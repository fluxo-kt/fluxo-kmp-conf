package fluxo.conf.impl.kotlin

import envOrPropList
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.android.hasRoomPlugin
import fluxo.conf.impl.envOrPropFlagValue
import fluxo.log.FluxoProblem
import fluxo.log.i
import fluxo.log.l
import fluxo.log.logDecision
import fluxo.log.reportProblem
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
        project.reportProblem(
            FluxoProblem.KOTLIN_VERSIONS,
            "$msg [allowed via -P$ALLOW_KOTLIN_STDLIB_SKEW_PROP]",
        )
    }

    // Note: apiVersion can't be greater than languageVersion!
    var lang = kotlinLangVersion?.toKotlinLangVersion()
    var api = kotlinApiVersion?.toKotlinLangVersion()?.takeIf { it != lang }
    if (api != null && lang == null) {
        lang = api
        api = null
    }
    if (api != null && lang != null && api > lang) {
        project.reportProblem(
            FluxoProblem.KOTLIN_VERSIONS,
            "Kotlin API version is downgraded from $api to $lang" +
                ", as it can't be greater than the language version.",
            fix = "Set kotlinApiVersion to $lang or lower, or raise kotlinLangVersion.",
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

    val defaultsOff = kotlinDefaultsOff(project)
    val progressive = (progressiveMode ?: true) && KotlinDefault.PROGRESSIVE !in defaultsOff

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
    val resolvedOptIns = resolveOptIns(DEFAULT_OPT_INS + optIns)
    val optIns = prepareOptIns(
        optIns = resolvedOptIns.everywhere,
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
        useJdkRelease = KotlinDefault.JDK_RELEASE !in defaultsOff,

        progressive = progressive,
        defaultsOff = defaultsOff,
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
        platformOptIns = resolvedOptIns,
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
        i(
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
 * The JVM target of a module that sets none. It must not follow whichever JDK happens to run
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

/**
 * Explains a failure of JVM compile task [taskName] in a module that sets no JVM target.
 * Up to fluxo-kmp-conf 0.15 the target followed the JDK running Gradle, so on a newer JDK the
 * [defaultJvmTarget] is a silent downgrade, and the compile errors it causes (an unresolved JDK
 * API, inlining code built for a newer target) never mention it. Printed only when that task
 * fails: a line on every build would be noise long after the upgrade.
 */
internal fun FluxoConfigurationExtensionImpl.hintDefaultedJvmTarget(
    taskName: String,
    target: String,
) {
    val major = target.toJvmMajorVersion()
    if (JRE_VERSION <= major) return
    val module = project.path
    ctx.buildEndReport.hintOnFailure(
        taskPath = if (module == ":") ":$taskName" else "$module:$taskName",
        hint = "w: Module '$module' sets no jvmTarget, so it compiles for JVM $major, " +
            "the default since fluxo-kmp-conf 0.16 (it used to follow the JDK running Gradle, " +
            "$JRE_VERSION). " +
            "If the failure is a JDK API newer than $major, or inlining code built for a newer " +
            "JVM target, set jvmTarget in fkcSetup* or the version catalog.",
    )
}

/** See [defaultJvmTarget]. */
private const val LIBRARY_JVM_TARGET = JRE_17

/**
 * The compiler defaults switched off for this module: `DISABLE_KOTLIN_DEFAULTS` first, which no
 * DSL setting can turn back on (so CI can rely on it), then the module's DSL with parent
 * inheritance. One decision line names them.
 */
private fun FluxoConfigurationExtensionImpl.kotlinDefaultsOff(
    project: Project,
): Set<KotlinDefault> {
    val build = parseDisabledKotlinDefaults(project.envOrPropList(DISABLE_KOTLIN_DEFAULTS))
    // Each default with the module setting that switches it off; unset means on.
    val settings = mapOf(
        KotlinDefault.JSR305 to jsr305Strict,
        KotlinDefault.VALIDATE_BYTECODE to validateBytecode,
        KotlinDefault.EMIT_JVM_TYPE_ANNOTATIONS to emitJvmTypeAnnotations,
        KotlinDefault.DONT_WARN_ON_ERROR_SUPPRESSION to dontWarnOnErrorSuppression,
        KotlinDefault.EXPECT_ACTUAL_CLASSES to expectActualClasses,
        KotlinDefault.SUPPRESS_VERSION_WARNINGS to suppressVersionWarnings,
        KotlinDefault.RETURN_VALUE_CHECKER to returnValueChecker,
        KotlinDefault.ANNOTATION_DEFAULT_TARGET to annotationDefaultTargetParamProperty,
        KotlinDefault.CONSISTENT_DATA_CLASS_COPY_VISIBILITY to consistentDataClassCopyVisibility,
        KotlinDefault.WHEN_EXPRESSIONS_INDY to whenExpressionsIndy,
        KotlinDefault.EXTRA_WARNINGS to extraWarnings,
        KotlinDefault.PROGRESSIVE to progressiveMode,
        KotlinDefault.JDK_RELEASE to useJdkRelease,
    )
    check(settings.keys == KotlinDefault.entries.toSet()) {
        "Kotlin defaults without a module setting: ${KotlinDefault.entries - settings.keys}"
    }
    val module = settings.filterValues { it == false }.keys
    val off = build + module
    if (off.isNotEmpty()) {
        ctx.logDecision(
            project,
            setting = "Kotlin defaults off",
            value = off.joinToString { it.switchName },
            reason = when {
                module.isEmpty() -> DISABLE_KOTLIN_DEFAULTS
                build.isEmpty() -> "fkcSetup* settings"
                else -> "$DISABLE_KOTLIN_DEFAULTS and fkcSetup* settings"
            },
            howToChange = "$DISABLE_KOTLIN_DEFAULTS, or the matching fkcSetup* setting",
        )
    }
    return off
}

private const val DISABLE_KOTLIN_DEFAULTS = "DISABLE_KOTLIN_DEFAULTS"
