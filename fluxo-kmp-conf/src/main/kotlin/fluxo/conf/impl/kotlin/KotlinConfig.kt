@file:Suppress("MagicNumber", "ReturnCount")

package fluxo.conf.impl.kotlin

import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion as KotlinLangVersion

/**
 * Per-project Kotlin configuration.
 *
 * @see FluxoConfigurationExtensionImpl.KotlinConfig
 */
@Suppress("LongParameterList")
internal class KotlinConfig(
    // https://kotlinlang.org/docs/compatibility-modes.html
    /** Can't be lower than [api]! */
    val lang: KotlinLangVersion?,
    val api: KotlinLangVersion?,
    val tests: KotlinLangVersion?,
    val coreLibs: String,

    /** Explicit, or derived when unset (see `defaultJvmTarget`). */
    val jvmTarget: String,
    val jvmTargetInt: Int,
    /** `false` when [jvmTarget] was derived: Android then keeps AGP's own default (R10). */
    val jvmTargetExplicit: Boolean,
    val jvmTestTarget: String?,
    val jvmToolchain: Boolean,
    val useJdkRelease: Boolean,

    val progressive: Boolean,
    /** Compiler defaults switched off for this module (DSL or `DISABLE_KOTLIN_DEFAULTS`). */
    val defaultsOff: Set<KotlinDefault>,
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
    val useKotlinCompose: Boolean,
    val setupCoroutines: Boolean,
    val setupSerialization: Boolean,
    /** Opt-ins for every compilation. */
    val optIns: Set<String>,
    /** Opt-ins to markers that exist only on some platforms ([ResolvedOptIns.forPlatform]). */
    val platformOptIns: ResolvedOptIns,
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
        isAndroid: Boolean,
        latestSettings: Boolean = false,
    ): String? {
        if (isAndroid && !jvmTargetExplicit) {
            return null
        }
        if (latestSettings) {
            return lastSupportedJvmMajorVersion(jvmToolchain).asJvmTargetVersion()
        }
        if (isTest) {
            jvmTestTarget?.let { return it }
        }
        return jvmTarget
    }

    /** @see ANDROID_SAFE_JVM_TARGET */
    val useSafeAndroidOptions get() = jvmTargetInt > ANDROID_SAFE_JVM_TARGET
}
