package fluxo.conf.impl.android

import com.android.builder.core.ToolsRevisionUtils
import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.data.BuildConstants
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import fluxo.log.FluxoProblem
import fluxo.log.reportProblem
import org.gradle.api.Project
import org.gradle.api.plugins.PluginAware

/** Current AndroidX libraries declare `minSdkVersion="23"`: below it the manifest merge fails. */
internal const val DEFAULT_ANDROID_MIN_SDK: Int = 23

/**
 * The highest compileSdk the consumer's AGP recommends (AGP warns above it), so the default moves
 * with their AGP instead of going stale in this plugin. Read from `ToolsRevisionUtils`, present
 * with this name and type from AGP 8.0 to 9.4 at least (sources read 2026-10-04); a later AGP
 * that drops it gets [FALLBACK_ANDROID_COMPILE_SDK].
 * `apiLevel` is deprecated in AGP 9.4 for `androidApiLevel`, which AGP 8.7 lacks.
 */
@Suppress("DEPRECATION")
internal fun agpMaxRecommendedCompileSdk(): Int = try {
    ToolsRevisionUtils.MAX_RECOMMENDED_COMPILE_SDK_VERSION.apiLevel
} catch (_: LinkageError) {
    FALLBACK_ANDROID_COMPILE_SDK
}

/** Google Play has required target SDK 36 since 2026-08-31. */
private const val FALLBACK_ANDROID_COMPILE_SDK: Int = 36

/**
 * @see org.jetbrains.kotlin.gradle.utils.androidPluginIds
 * @see org.jetbrains.kotlin.gradle.utils.findAppliedAndroidPluginIdOrNull
 */
internal const val ANDROID_APP_PLUGIN_ID = "com.android.application"
internal const val ANDROID_LIB_PLUGIN_ID = "com.android.library"

/**
 * KMP-aware Android library plugin id (required from AGP `9.0`; fluxo configures it from AGP
 * `8.8`, see [withKmpAndroidLibPlugin]).
 *
 * Replaces the `com.android.library` + `kotlin("multiplatform")` co-application,
 * which AGP 9 hard-rejects. The plugin auto-creates a `KotlinMultiplatformAndroidLibraryTarget`
 * via the `kotlin { android { } }` DSL block (no separate `androidTarget()` call needed).
 *
 * @see <a href="https://developer.android.com/kotlin/multiplatform/plugin">AGP KMP Library docs</a>
 */
internal const val ANDROID_KMP_LIB_PLUGIN_ID = "com.android.kotlin.multiplatform.library"

internal const val ANDROID_LINT_PLUGIN_ID = "com.android.lint"

// https://developer.android.com/jetpack/androidx/releases/room#2.6.0-alpha02
internal const val ANDROIDX_ROOM_PLUGIN_ID = "androidx.room"

internal const val ANDROID_EXT_NAME = "android"

internal val PluginAware.hasAndroidAppPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROID_APP_PLUGIN_ID)

internal val PluginAware.hasAndroidLibPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROID_LIB_PLUGIN_ID)

/**
 * `com.android.kotlin.multiplatform.library` (required from AGP `9.0`).
 * Mutually exclusive with [hasAndroidLibPlugin] under AGP 9+.
 */
internal val PluginAware.hasAndroidKmpLibPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROID_KMP_LIB_PLUGIN_ID)

/**
 * Runs [action] once `com.android.kotlin.multiplatform.library` is applied, if this AGP has the
 * target type fluxo configures it through. AGP 8.4–8.7 ship the plugin with an older target
 * type, so there every fluxo call into it would fail with `NoClassDefFoundError`; fluxo leaves
 * such a module to AGP's own defaults and says so once per build. Probed by class presence, so
 * any AGP that has the type works.
 */
internal fun Project.withKmpAndroidLibPlugin(ctx: FluxoKmpConfContext, action: () -> Unit) {
    pluginManager.withPlugin(ANDROID_KMP_LIB_PLUGIN_ID) {
        val hasTargetApi = runCatching {
            Class.forName(KMP_ANDROID_LIB_TARGET, false, FluxoProblem::class.java.classLoader)
        }.isSuccess
        when {
            hasTargetApi -> action()

            ctx.firstInBuild("kmp-android-lib-target-missing") -> reportProblem(
                FluxoProblem.SETUP_STEP_SKIPPED,
                "This AGP's `$ANDROID_KMP_LIB_PLUGIN_ID` lacks `$KMP_ANDROID_LIB_TARGET` " +
                    "(added in AGP 8.8), so fluxo doesn't set up its Android targets: no " +
                    "namespace and SDK defaults, Lint or Detekt setup for them.",
                fix = "Use AGP 8.8 or newer, or `com.android.library` with fluxo's " +
                    "`androidLibrary()` target on AGP 8.",
            )
        }
    }
}

private const val KMP_ANDROID_LIB_TARGET =
    "com.android.build.api.dsl.KotlinMultiplatformAndroidLibraryTarget"

internal val PluginAware.hasRoomPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROIDX_ROOM_PLUGIN_ID)


internal const val ALIAS_LEAK_CANARY = "square-leakcanary"

internal const val ALIAS_DESUGAR_LIBS = "android-desugarLibs"

// Old but sometimes useful annotation lib for Java/Kotlin compatibility.
// Doesn't update, so no need for the version catalog.
// https://mvnrepository.com/artifact/com.google.code.findbugs/jsr305
internal const val JSR305_DEPENDENCY = "com.google.code.findbugs:jsr305:3.0.2"

internal const val RELEASE = "release"
internal const val DEBUG = "debug"


internal val ANDROID_PLUGIN_NOT_IN_CLASSPATH_ERROR = """
    Android Gradle Plugin (AGP) is not found in the classpath which prevents android KMP target from initialization.
    Please apply AGP in the root Gradle module (in `build.gradle.kts`) like this:
    ```
    plugins {
        // For AGP < 9.0:
        id("$ANDROID_LIB_PLUGIN_ID") apply false
        // OR for AGP >= 8.8 with KMP+Android (REQUIRED on AGP 9.0+):
        id("$ANDROID_KMP_LIB_PLUGIN_ID") apply false

        id("$KOTLIN_MPP_PLUGIN_ID") apply false
        id("${BuildConstants.PLUGIN_ID}")
    }
    ```
""".trimIndent()
