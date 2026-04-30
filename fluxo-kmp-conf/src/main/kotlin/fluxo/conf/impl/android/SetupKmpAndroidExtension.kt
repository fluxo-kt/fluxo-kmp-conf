@file:Suppress("UnstableApiUsage")

package fluxo.conf.impl.android

import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryExtension
import com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget
import fluxo.conf.dsl.impl.FluxoConfigurationExtensionImpl
import fluxo.conf.impl.kotlin.ksp
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
 * @see <a href="https://developer.android.com/kotlin/multiplatform/plugin">AGP KMP Library docs</a>
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
 * `buildToolsVersion`, plus Room KSP args when `setupRoom = true`. The new extension does NOT
 * have `defaultConfig`, `buildTypes`, `productFlavors`, or `resourceConfigurations` — those
 * are either unsupported or surfaced via a different DSL.
 *
 * `testInstrumentationRunner` is intentionally NOT auto-defaulted here: the AGP-9 KMP+Android
 * path requires the consumer to explicitly opt-in to device tests via `withDeviceTestBuilder
 * { }`, and a blanket `withDeviceTest { }` registration eagerly creates the
 * `androidDeviceTest*` configurations whether the consumer wanted them or not (verified by
 * configuration-resolution failure in checks/kmp during the 0.14.0 audit). This is an
 * upstream-imposed difference vs. AGP-8's always-present `defaultConfig.test
 * InstrumentationRunner`. Consumers that want the legacy `androidx.test.runner.AndroidJUnit
 * Runner` default must specify it inside their own `withDeviceTestBuilder { }.configure {
 * instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }` block.
 */
private fun KotlinMultiplatformAndroidLibraryExtension.applyFluxoDefaults(
    conf: FluxoConfigurationExtensionImpl,
) {
    applyNamespace(conf)
    applyCompileSdk(conf)
    applyMinSdk(conf)
    applyBuildToolsVersion(conf)
    if (conf.kotlinConfig.setupRoom) applyRoomKspArgs(conf)
}

private fun KotlinMultiplatformAndroidLibraryExtension.applyNamespace(
    conf: FluxoConfigurationExtensionImpl,
) {
    if (!namespace.isNullOrBlank()) return
    val ns = conf.androidNamespace
    if (ns.isNotBlank()) {
        namespace = ns
        conf.project.logger.l("Android namespace '$ns' (KMP+Android)")
    } else {
        // The new plugin REQUIRES namespace; surface clearly rather than letting AGP fail later
        // with `Namespace not specified` mid-task-graph.
        conf.project.logger.e(
            "Required Android namespace IS EMPTY for `$ANDROID_KMP_LIB_PLUGIN_ID` " +
                "(set `androidNamespace = \"...\"` in `fluxoConfiguration { }` " +
                "or `kotlin { android { namespace = \"...\" } }` directly).",
        )
    }
}

private fun KotlinMultiplatformAndroidLibraryExtension.applyCompileSdk(
    conf: FluxoConfigurationExtensionImpl,
) {
    if (compileSdk != null || !compileSdkPreview.isNullOrBlank()) return
    when (val v = conf.androidCompileSdk) {
        is Int -> compileSdk = v
        is String -> compileSdkPreview = v
        else -> conf.project.logger.e(
            "Unsupported `androidCompileSdk` type ${v::class.simpleName}; " +
                "expected Int or String preview. Ignored.",
        )
    }
}

private fun KotlinMultiplatformAndroidLibraryExtension.applyMinSdk(
    conf: FluxoConfigurationExtensionImpl,
) {
    if (minSdk != null || !minSdkPreview.isNullOrBlank()) return
    when (val v = conf.androidMinSdk) {
        is Int -> minSdk = v
        is String -> minSdkPreview = v
        else -> conf.project.logger.e(
            "Unsupported `androidMinSdk` type ${v::class.simpleName}; " +
                "expected Int or String preview. Ignored.",
        )
    }
}

private fun KotlinMultiplatformAndroidLibraryExtension.applyBuildToolsVersion(
    conf: FluxoConfigurationExtensionImpl,
) {
    if (!buildToolsVersion.isNullOrBlank()) return
    conf.androidBuildToolsVersion?.takeIf { it.isNotBlank() }?.let { buildToolsVersion = it }
}

/**
 * Mirrors `setupAndroidCommon`'s legacy Room wiring. The KSP-arg part (`room.generateKotlin` /
 * `incremental` / `schemaLocation`) is plugin-agnostic — it operates on the project-level
 * `KspExtension`, not on the Android extension — so it ports cleanly to the AGP-9 KMP+Android
 * path.
 *
 * The legacy path also calls `sourceSets["androidTest"].assets.srcDir(roomSchemasDir)` to
 * expose the schemas to instrumented tests as test-app assets. That source-set lookup uses
 * AGP's legacy `LibraryExtension.sourceSets` collection, which the AGP-9 KMP+Android extension
 * does NOT expose (KMP source sets live on `KotlinMultiplatformExtension.sourceSets` under
 * different names like `androidDeviceTest`, with a different `resources`/`kotlin` shape and no
 * direct `assets` accessor at the time of writing). Wire that yourself if you run instrumented
 * Room tests on AGP 9 KMP+Android — the message at info-level documents the migration anchor.
 */
private fun KotlinMultiplatformAndroidLibraryExtension.applyRoomKspArgs(
    conf: FluxoConfigurationExtensionImpl,
) {
    val project = conf.project
    val schemasDir = "${project.projectDir}/schemas"
    project.ksp {
        arg("room.generateKotlin", "true")
        arg("room.incremental", "true")
        arg("room.schemaLocation", schemasDir)
    }
    project.logger.l(
        "Room KSP args wired (KMP+Android, schemas at '$schemasDir'). For instrumented " +
            "Room tests under AGP 9 KMP+Android, attach `$schemasDir` to the " +
            "`androidDeviceTest` source set's resources manually — `LibraryExtension." +
            "sourceSets[\"androidTest\"].assets.srcDir(...)` from the legacy path is NOT " +
            "available on `KotlinMultiplatformAndroidLibraryExtension`.",
    )
}
