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

    val jvmTarget: String?,
    val jvmTargetInt: Int,
    val jvmTestTarget: String?,
    val jvmToolchain: Boolean,
    val useJdkRelease: Boolean,

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

    val allowManualHierarchy: Boolean,
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
            return lastSupportedJvmTargetVersion(jvmToolchain)
        }
        if (isTest) {
            jvmTestTarget?.let { return it }
        }
        return jvmTarget
    }

    /** @see ANDROID_SAFE_JVM_TARGET */
    val useSafeAndroidOptions get() = jvmTargetInt > ANDROID_SAFE_JVM_TARGET
}
