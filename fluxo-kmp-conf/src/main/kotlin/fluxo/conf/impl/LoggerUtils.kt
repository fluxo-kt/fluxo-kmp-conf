package fluxo.conf.impl

import org.gradle.api.logging.Logger


private const val TAG = "FluxoKmpConf"

@Volatile
internal var SHOW_DEBUG_LOGS = true


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

internal fun Logger.l(message: String) = lifecycle("> Conf $message")

internal fun Logger.i(message: String) = l(message)


internal fun Logger.w(message: String, e: Throwable? = null) = warn("> Conf! $message", e)

internal fun Logger.e(message: String, e: Throwable? = null) = error("> Conf! $message", e)

