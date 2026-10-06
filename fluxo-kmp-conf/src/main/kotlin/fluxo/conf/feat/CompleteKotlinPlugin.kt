package fluxo.conf.feat

import fluxo.conf.FluxoKmpConfContext
import loadKmmCodeCompletion

/**
 * `LOAD_KMM_CODE_COMPLETION` used to apply the `com.louiscad.complete-kotlin` plugin, which
 * downloads another OS's Kotlin/Native distribution to copy the platform libraries (e.g. iOS
 * `platform.Foundation` on Linux) that the host's own distribution lacks. It downloads only for
 * targets whose `klib/platform/<target>` is missing, and every host distribution (Linux, macOS,
 * Windows) ships all targets' platform libraries on the whole supported range (Kotlin 2.1.21 and
 * 2.4.20 distributions read 2026-10-06), so it downloaded nothing. An iOS source set using
 * `platform.Foundation` compiled on Linux x86-64 without it, on Kotlin 2.2.21 and 2.4.20.
 */
internal fun FluxoKmpConfContext.warnIfCodeCompletionFlagSet() {
    if (!rootProject.loadKmmCodeCompletion()) {
        return
    }
    buildEndReport.warn {
        "'$LOAD_KMM_CODE_COMPLETION_FLAG' does nothing any more and can be removed: " +
            "the Kotlin/Native distribution already contains the platform libraries " +
            "of every target."
    }
}


internal const val LOAD_KMM_CODE_COMPLETION_FLAG = "LOAD_KMM_CODE_COMPLETION"
