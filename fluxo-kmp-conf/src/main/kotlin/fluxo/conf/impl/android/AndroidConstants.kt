package fluxo.conf.impl.android

import fluxo.conf.data.BuildConstants
import fluxo.conf.impl.kotlin.KOTLIN_MPP_PLUGIN_ID
import org.gradle.api.plugins.PluginAware

internal const val DEFAULT_ANDROID_MIN_SDK: Int = 21

internal const val DEFAULT_ANDROID_TARGET_SDK: Int = 34

internal const val DEFAULT_ANDROID_COMPILE_SDK: Int = 34

/**
 * @see org.jetbrains.kotlin.gradle.utils.androidPluginIds
 * @see org.jetbrains.kotlin.gradle.utils.findAppliedAndroidPluginIdOrNull
 */
internal const val ANDROID_APP_PLUGIN_ID = "com.android.application"
internal const val ANDROID_LIB_PLUGIN_ID = "com.android.library"

/**
 * KMP-aware Android library plugin id (AGP `>= 8.8.0`, required from AGP `9.0`).
 *
 * Replaces the `com.android.library` + `kotlin("multiplatform")` co-application,
 * which AGP 9 hard-rejects. The plugin auto-creates a `KotlinMultiplatformAndroidLibraryTarget`
 * via the `kotlin { android { } }` DSL block (no separate `androidTarget()` call needed).
 *
 * @see <a href="https://developer.android.com/kotlin/multiplatform/plugin">Set up the Android Gradle Library Plugin for KMP</a>
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
 * `com.android.kotlin.multiplatform.library` (AGP `>= 8.8.0`, required from AGP `9.0`).
 * Mutually exclusive with [hasAndroidLibPlugin] under AGP 9+.
 */
internal val PluginAware.hasAndroidKmpLibPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROID_KMP_LIB_PLUGIN_ID)

/**
 * Any AGP plugin that produces an Android-ish artifact: legacy `com.android.application`,
 * legacy `com.android.library`, or the AGP-9 KMP-aware `com.android.kotlin.multiplatform.library`.
 *
 * Use this for "do we need to wire the publish/verification pipeline for an Android artifact?"
 * checks. For "is this the legacy `com.android.library` extension specifically?", use
 * [hasAndroidLibPlugin].
 */
internal val PluginAware.hasAnyAndroidArtifactPlugin: Boolean
    get() = hasAndroidAppPlugin || hasAndroidLibPlugin || hasAndroidKmpLibPlugin

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
