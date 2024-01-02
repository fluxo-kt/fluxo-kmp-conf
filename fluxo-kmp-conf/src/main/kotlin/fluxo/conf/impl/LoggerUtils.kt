@file:Suppress("TooManyFunctions")

package fluxo.conf.impl

import org.gradle.api.logging.Logger


private const val TAG = "FluxoKmpConf"
private const val LOG_TASK_PREFIX = "> "
private const val CONF = LOG_TASK_PREFIX + "Conf"
private const val FLUXO = "Fluxo"

@Volatile
internal var SHOW_DEBUG_LOGS = false


internal fun Logger.t(message: String) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("{}Fv: {}", LOG_TASK_PREFIX, message)
    } else {
        trace("{}{} {}", LOG_TASK_PREFIX, TAG, message)
    }
}

internal fun Logger.t(message: String, e: Throwable?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("${LOG_TASK_PREFIX}Fv: $message", e)
    } else {
        trace("$LOG_TASK_PREFIX$TAG: $message", e)
    }
}

internal fun Logger.v(message: String, e: Throwable? = null) = t(message, e)


internal fun Logger.d(message: String, e: Throwable? = null) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("${LOG_TASK_PREFIX}F: $message", e)
    } else {
        debug("$LOG_TASK_PREFIX$TAG: $message", e)
    }
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("{}F: $message", LOG_TASK_PREFIX, arg1, arg2)
    } else {
        debug("{}$TAG: $message", LOG_TASK_PREFIX, arg1, arg2)
    }
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?, arg3: Any?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("{}F: $message", LOG_TASK_PREFIX, arg1, arg2, arg3)
    } else {
        debug("{}$TAG: $message", LOG_TASK_PREFIX, arg1, arg2, arg3)
    }
}


internal fun Logger.i(message: String) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("${LOG_TASK_PREFIX}Fi: {}", message)
    } else {
        info("{} {}", CONF, message)
    }
}

internal fun Logger.i(message: String, arg1: Any?) {
    if (SHOW_DEBUG_LOGS) {
        lifecycle("{}Fi: $message", LOG_TASK_PREFIX, arg1)
    } else {
        info("{} $message", CONF, arg1)
    }
}


internal fun Logger.l(message: String) = lifecycle("{} {}", CONF, message)
internal fun Logger.l(message: String, vararg args: Any) = lifecycle("$CONF $message", *args)


internal fun Logger.w(message: String, e: Throwable? = null) = warn("w: $CONF! $message", e)


internal fun Logger.e(message: String) = error("e: {} {}", FLUXO, message)

internal fun Logger.e(message: String, e: Throwable?) = error("e: $FLUXO $message", e)

internal fun Logger.e(message: String, arg1: Any?) = error("e : {} $message", FLUXO, arg1)

internal fun Logger.e(message: String, vararg args: Any?) = error("e: $FLUXO $message", *args)
