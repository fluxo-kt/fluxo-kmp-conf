package fluxo.conf.impl.android

import fluxo.conf.data.BuildConstants
import org.gradle.api.plugins.PluginAware

/**
 * @see org.jetbrains.kotlin.gradle.utils.androidPluginIds
 * @see org.jetbrains.kotlin.gradle.utils.findAppliedAndroidPluginIdOrNull
 */
internal const val ANDROID_APP_PLUGIN_ID = "com.android.application"
internal const val ANDROID_LIB_PLUGIN_ID = "com.android.library"

// https://developer.android.com/jetpack/androidx/releases/room#2.6.0-alpha02
internal const val ANDROIDX_ROOM_PLUGIN_ID = "androidx.room"

internal val PluginAware.hasAndroidAppPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROID_APP_PLUGIN_ID)

internal val PluginAware.hasRoomPlugin: Boolean
    get() = pluginManager.hasPlugin(ANDROIDX_ROOM_PLUGIN_ID)


internal const val ALIAS_LEAK_CANARY = "square-leakcanary"

internal const val ALIAS_DESUGAR_LIBS = "android-desugarLibs"

internal const val ALIAS_ANDROIDX_COMPOSE_COMPILER = "androidx-compose-compiler"

// Old but sometimes useful annotation lib for Java/Kotlin compatibility.
// Doesn't update, so no need for the version catalog.
internal const val JSR305_DEPENDENCY = "com.google.code.findbugs:jsr305:3.0.2"

internal const val RELEASE = "release"
internal const val DEBUG = "debug"


internal val ANDROID_PLUGIN_NOT_IN_CLASSPATH_ERROR = """
    Android Gradle Plugin (AGP) is not found in the classpass which prevents android KMP target from initialization.
    Please apply AGP in the root Gradle module (in `build.gradle.kts`) like this:
    ```
    plugins {
        id("com.android.library") apply false
        id("org.jetbrains.kotlin.multiplatform") apply false
        id("${BuildConstants.PLUGIN_ID}")
    }
    ```
""".trimIndent()
