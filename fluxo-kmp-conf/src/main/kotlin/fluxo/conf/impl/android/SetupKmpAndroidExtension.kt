@file:Suppress("UnstableApiUsage")

package fluxo.conf.impl.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.kotlin.mppExtOrNull
import fluxo.conf.impl.withType
import fluxo.log.e
import fluxo.log.l
import org.gradle.api.Project

/**
 * Auto-configures the AGP-9 KMP+Android library plugin
 * (`com.android.kotlin.multiplatform.library`).
 *
 * The plugin auto-creates a [KotlinMultiplatformAndroidLibraryTarget] (which itself implements
 * [KotlinMultiplatformAndroidLibraryExtension]) when the consumer writes `kotlin { android { } }`.
 * This function is the bridge between [FluxoConfigurationExtensionImpl] (`androidNamespace`,
 * `androidCompileSdk`, `androidMinSdk`, `androidBuildToolsVersion`) and the new extension type —
 * the legacy `setupAndroidCommon` path operates on `TestedExtension`, which the new extension
 * does NOT implement, so the two paths are necessarily separate.
 *
 * Defensive against double-config: properties are only set if our config has a non-null value
 * AND the consumer hasn't already set them in their build script (we don't override explicit
 * consumer configuration). Idempotent.
 *
 * @see <a href="https://developer.android.com/kotlin/multiplatform/plugin">Set up the Android Gradle Library Plugin for KMP</a>
 */
internal fun Project.setupKmpAndroidExtension(conf: FluxoConfigurationExtensionImpl) {
    pluginManager.withPlugin(ANDROID_KMP_LIB_PLUGIN_ID) {
        val mppExt = mppExtOrNull ?: run {
            logger.e(
                "$ANDROID_KMP_LIB_PLUGIN_ID applied without a Kotlin Multiplatform extension; " +
                    "skipping Fluxo auto-config of `kotlin.android { }`.",
            )
            return@withPlugin
        }

        mppExt.targets.withType<KotlinMultiplatformAndroidLibraryTarget>().configureEach {
            // The target IS the extension on the AGP-9 KMP+Android plugin.
            applyFluxoDefaults(conf)
        }
    }
}

/**
 * Applies Fluxo defaults to a [KotlinMultiplatformAndroidLibraryExtension]; only fills in slots
 * the consumer hasn't claimed.
 *
 * Mirrors the subset of [setupAndroidCommon] that the new extension exposes: `namespace`,
 * `compileSdk` (Int or `compileSdkPreview` String), `minSdk` (Int or `minSdkPreview` String),
 * `buildToolsVersion`. The new extension does NOT have `defaultConfig`, `buildTypes`,
 * `productFlavors`, `testInstrumentationRunner`, `resourceConfigurations`, etc. — those are
 * either unsupported or surfaced via a different DSL (`withDeviceTestBuilder { }` for
 * instrumentation runner). Consumers needing them stay on the legacy `com.android.library` path.
 */
private fun KotlinMultiplatformAndroidLibraryExtension.applyFluxoDefaults(
    conf: FluxoConfigurationExtensionImpl,
) {
    val project = conf.project
    val ns = conf.androidNamespace
    if (namespace.isNullOrBlank() && ns.isNotBlank()) {
        namespace = ns
        project.logger.l("Android namespace '$ns' (KMP+Android)")
    } else if (namespace.isNullOrBlank()) {
        // The new plugin REQUIRES namespace; surface clearly rather than letting AGP fail later
        // with `Namespace not specified` mid-task-graph.
        project.logger.e(
            "Required Android namespace IS EMPTY for `$ANDROID_KMP_LIB_PLUGIN_ID` " +
                "(set `androidNamespace = \"...\"` in `fluxoConfiguration { }` " +
                "or `kotlin { android { namespace = \"...\" } }` directly).",
        )
    }

    if (compileSdk == null && compileSdkPreview.isNullOrBlank()) {
        when (val v = conf.androidCompileSdk) {
            is Int -> compileSdk = v
            is String -> compileSdkPreview = v
            null -> Unit
            else -> project.logger.e(
                "Unsupported `androidCompileSdk` type ${v::class.simpleName}; " +
                    "expected Int or String preview. Ignored.",
            )
        }
    }

    if (minSdk == null && minSdkPreview.isNullOrBlank()) {
        when (val v = conf.androidMinSdk) {
            is Int -> minSdk = v
            is String -> minSdkPreview = v
            null -> Unit
            else -> project.logger.e(
                "Unsupported `androidMinSdk` type ${v::class.simpleName}; " +
                    "expected Int or String preview. Ignored.",
            )
        }
    }

    if (buildToolsVersion.isNullOrBlank()) {
        conf.androidBuildToolsVersion?.takeIf { it.isNotBlank() }?.let {
            buildToolsVersion = it
        }
    }
}
