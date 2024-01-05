@file:Suppress("TooManyFunctions")

package fluxo.conf.impl

import org.gradle.api.logging.Logger


private const val TAG = "FluxoKmpConf"

@Volatile
internal var SHOW_DEBUG_LOGS = false


internal fun Logger.t(message: String) = when {
    !SHOW_DEBUG_LOGS -> trace("{} {}", TAG, message)
    else -> lifecycle("v: {}", message)
}

internal fun Logger.t(message: String, e: Throwable?) = when {
    !SHOW_DEBUG_LOGS -> trace("$TAG: $message", e)
    else -> lifecycle("v: $message", e)
}

internal fun Logger.v(message: String) = t(message)

internal fun Logger.v(message: String, e: Throwable?) = t(message, e)


internal fun Logger.d(message: String, e: Throwable? = null) = when {
    !SHOW_DEBUG_LOGS -> debug("$TAG: $message", e)
    else -> lifecycle("d: $message", e)
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?) = when {
    !SHOW_DEBUG_LOGS -> debug("$TAG: $message", arg1, arg2)
    else -> lifecycle("d: $message", arg1, arg2)
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?, arg3: Any?) = when {
    !SHOW_DEBUG_LOGS -> debug("$TAG: $message", arg1, arg2, arg3)
    else -> lifecycle("d: $message", arg1, arg2, arg3)
}


internal fun Logger.i(message: String) = when {
    !SHOW_DEBUG_LOGS -> debug(message)
    else -> lifecycle("i: {}", message)
}

internal fun Logger.i(message: String, arg1: Any?) = when {
    !SHOW_DEBUG_LOGS -> debug(message, arg1)
    else -> lifecycle("i: $message", arg1)
}


internal fun Logger.l(message: String) = lifecycle(message)
internal fun Logger.l(message: String, vararg args: Any) = lifecycle(message, *args)


internal fun Logger.w(message: String) = warn("w: {}", message)
internal fun Logger.w(message: String, e: Throwable?) = warn("w: $message", e)


internal fun Logger.e(message: String) = error("e: {}", message)

internal fun Logger.e(message: String, e: Throwable?) = error("e: $message", e)

internal fun Logger.e(message: String, arg1: Any?) = error("e: $message", arg1)

internal fun Logger.e(message: String, vararg args: Any?) = error("e: $message", *args)
