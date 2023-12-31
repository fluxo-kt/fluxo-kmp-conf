@file:Suppress("TooManyFunctions")

package fluxo.conf.impl

import org.gradle.api.logging.Logger


private const val TAG = "FluxoKmpConf"
private const val CONF = "> Conf"

@Volatile
internal var SHOW_DEBUG_LOGS = false


internal fun Logger.t(message: String, e: Throwable? = null) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("> Fv: $message", e)
    } else {
        trace("> $TAG: $message", e)
    }
}

internal fun Logger.v(message: String, e: Throwable? = null) = t(message, e)


internal fun Logger.d(message: String, e: Throwable? = null) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("> F: $message", e)
    } else {
        debug("> $TAG: $message", e)
    }
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("> F: $message", arg1, arg2)
    } else {
        debug("> $TAG: $message", arg1, arg2)
    }
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?, arg3: Any?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("> F: $message", arg1, arg2, arg3)
    } else {
        debug("> $TAG: $message", arg1, arg2, arg3)
    }
}


internal fun Logger.i(message: String) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("> Fi: $message")
    } else {
        debug("$CONF $message")
    }
}

internal fun Logger.i(message: String, arg1: Any?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("> Fi: $message", arg1)
    } else {
        debug("$CONF $message", arg1)
    }
}


internal fun Logger.l(message: String) = lifecycle("$CONF $message")
internal fun Logger.l(message: String, vararg args: Any) = lifecycle("$CONF $message", *args)


internal fun Logger.w(message: String, e: Throwable? = null) = warn("$CONF! $message", e)

internal fun Logger.e(message: String, e: Throwable? = null) = error("$CONF! $message", e)

internal fun Logger.e(message: String, arg1: Any?) = error("{}! $message", CONF, arg1)
