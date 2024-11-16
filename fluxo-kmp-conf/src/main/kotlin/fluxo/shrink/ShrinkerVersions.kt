package fluxo.shrink

import fluxo.artifact.proc.JvmShrinker
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal val BUNDLED_R8_VERSION: String? = run {
    try {
        // https://r8.googlesource.com/r8/+refs
        // https://issuetracker.google.com/issues/193543616#comment4
        // https://mvnrepository.com/artifact/com.android.tools/r8
        /** Cannot use [com.android.tools.r8.Version.LABEL] directly here
         *  as it will be inlined during compilation. */
        com.android.tools.r8.Version.getVersionString().substringBefore(" (").trim()
    } catch (_: Throwable) {
        null
    }
}

internal val BUNDLED_PROGUARD_VERSION: String? = run {
    try {
        proguard.ProGuard.getVersion()
    } catch (_: Throwable) {
        null
    }
}

internal var PREFER_BUNDLED_R8 = AtomicBoolean(true)

internal var REMOTE_SHRINKER_VERSION = ConcurrentHashMap<JvmShrinker, String>()
