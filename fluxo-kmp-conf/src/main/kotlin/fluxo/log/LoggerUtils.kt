@file:Suppress("TooManyFunctions", "LoggingSimilarMessage")

package fluxo.log

import org.gradle.api.logging.Logger


private const val TAG = "FluxoKmpConf"

@Volatile
internal var SHOW_DEBUG_LOGS = false


private const val V = "v: "
private const val D = "d: "
private const val I = "i: "
private const val L = "   "
private const val W = "w: "
private const val E = "e: "


internal fun Logger.t(message: String) {
    when {
        SHOW_DEBUG_LOGS -> lifecycle("$V{}", message)
        isTraceEnabled -> trace("{} {}", TAG, message)
    }
}

internal fun Logger.t(message: String, e: Throwable?) {
    when {
        SHOW_DEBUG_LOGS -> lifecycle("$V$message", e)
        isTraceEnabled -> trace("$TAG: $message", e)
    }
}

internal inline fun Logger.t(message: () -> String) {
    if (SHOW_DEBUG_LOGS || isTraceEnabled) {
        t(message())
    }
}

internal inline fun Logger.tb(message: StringBuilder.() -> Unit) {
    if (SHOW_DEBUG_LOGS || isTraceEnabled) {
        t(buildString(message))
    }
}

internal fun Logger.v(message: String) = t(message)

internal fun Logger.v(message: String, e: Throwable?) = t(message, e)

internal inline fun Logger.v(message: () -> String) = t(message)

internal inline fun Logger.vb(message: StringBuilder.() -> Unit) = tb(message)


internal fun Logger.d(message: String, e: Throwable? = null) = when {
    !SHOW_DEBUG_LOGS -> debug("$TAG: $message", e)
    else -> lifecycle("$D$message", e)
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?) = when {
    !SHOW_DEBUG_LOGS -> debug("$TAG: $message", arg1, arg2)
    else -> lifecycle("$D$message", arg1, arg2)
}

internal fun Logger.d(message: String, arg1: Any?, arg2: Any?, arg3: Any?) = when {
    !SHOW_DEBUG_LOGS -> debug("$TAG: $message", arg1, arg2, arg3)
    else -> lifecycle("$D$message", arg1, arg2, arg3)
}


internal fun Logger.i(message: String) = when {
    !SHOW_DEBUG_LOGS -> info(message)
    else -> lifecycle("$I{}", message)
}

internal fun Logger.i(message: String, arg1: Any?) = when {
    !SHOW_DEBUG_LOGS -> info(message, arg1)
    else -> lifecycle("$I$message", arg1)
}


internal fun Logger.l(message: String) = lifecycle(L + message)
internal fun Logger.l(message: String, vararg args: Any) = lifecycle(L + message, *args)


internal fun Logger.w(message: String) = warn("$W{}", message)
internal fun Logger.w(message: String, e: Throwable?) = warn("$W$message", e)


internal fun Logger.e(message: String) = error("$E{}", message)

internal fun Logger.e(message: String, e: Throwable?) = error("$E$message", e)

internal fun Logger.e(message: String, arg1: Any?) = error("$E$message", arg1)

internal fun Logger.e(message: String, arg1: Any?, arg2: Any?) = error("$E$message", arg1, arg2)

internal fun Logger.e(message: String, vararg args: Any?) = error("$E$message", *args)
