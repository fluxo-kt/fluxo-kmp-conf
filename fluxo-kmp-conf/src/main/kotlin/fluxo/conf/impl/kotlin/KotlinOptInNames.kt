package fluxo.conf.impl.kotlin

import fluxo.conf.impl.kotlin.OptInPlatform.JVM
import fluxo.conf.impl.kotlin.OptInPlatform.NATIVE
import fluxo.conf.impl.kotlin.OptInPlatform.WASM_JS
import fluxo.conf.impl.kotlin.OptInPlatform.WASM_WASI

/**
 * Short names for `optIns`, so a consumer names an experimental API by its marker's simple name
 * (`ExperimentalUuidApi`) as well as its full one. The plugin opts in only to markers the consumer
 * names: a blanket list rots at both ends, as a marker missing from an older Kotlin or deprecated
 * by a newer one fails warnings-as-errors builds.
 *
 * The table is read from the sources of the Kotlin 2.4.20 stdlib (each platform's part) and the
 * newest stable kotlinx coroutines, serialization and datetime and Compose libraries; a marker it
 * lacks is still accepted by its full name. A marker that exists only on some platforms is passed
 * only to those platforms' compilations, where the compiler would otherwise report it unresolved.
 *
 * Kept free of Kotlin Gradle plugin types so it can be unit-tested (see AGENTS.md).
 */
internal enum class OptInPlatform { JVM, JS, WASM_JS, WASM_WASI, NATIVE }

/** Opt-ins for every compilation, and those for some platforms only (full name to platforms). */
internal class ResolvedOptIns(
    val everywhere: Set<String>,
    val platformOnly: Map<String, Set<OptInPlatform>>,
) {
    fun forPlatform(platform: OptInPlatform?): Set<String> =
        platformOnly.filterValues { platform in it }.keys
}

/**
 * Resolves `optIns` names: a full name passes through, a short name maps to its marker, and an
 * unknown short name fails, naming the closest ones, as the compiler would only warn that the
 * marker is unresolved.
 */
internal fun resolveOptIns(names: Collection<String>): ResolvedOptIns {
    val everywhere = LinkedHashSet<String>()
    val platformOnly = LinkedHashMap<String, Set<OptInPlatform>>()
    for (raw in names) {
        val name = raw.trim()
        if (name.isEmpty()) continue
        val marker = if ('.' in name) {
            MARKERS_BY_NAME[name] ?: OptInMarker(name, platforms = null)
        } else {
            MARKERS_BY_SHORT_NAME[name] ?: throw IllegalArgumentException(
                "optIns: unknown opt-in marker '$name'. " +
                    closestNames(name, MARKERS_BY_SHORT_NAME.keys).let {
                        if (it.isEmpty()) "" else "Did you mean ${it.joinToString(" or ")}? "
                    } +
                    "Use the marker's full name (e.g. 'kotlin.uuid.ExperimentalUuidApi') " +
                    "for one fluxo does not know.",
            )
        }
        when (val platforms = marker.platforms) {
            null -> everywhere += marker.name
            else -> platformOnly[marker.name] = platforms
        }
    }
    return ResolvedOptIns(everywhere, platformOnly)
}

private class OptInMarker(val name: String, val platforms: Set<OptInPlatform>?)

private fun marker(name: String, vararg platforms: OptInPlatform) =
    OptInMarker(name, platforms.toSet().takeIf { it.isNotEmpty() })

/** Platforms listed only for markers missing from some platform's stdlib. */
private val MARKERS = listOf(
    marker("kotlinx.cinterop.BetaInteropApi", NATIVE),
    marker("kotlin.wasm.unsafe.ComponentModelInternalApi", WASM_JS, WASM_WASI),
    marker("androidx.compose.runtime.tooling.ComposeToolingApi"),
    marker("kotlinx.coroutines.DelicateCoroutinesApi"),
    marker("androidx.compose.animation.ExperimentalAnimationApi"),
    marker("androidx.compose.animation.core.ExperimentalAnimationSpecApi"),
    marker("kotlin.reflect.ExperimentalAssociatedObjects", NATIVE, WASM_JS, WASM_WASI),
    marker("kotlin.concurrent.atomics.ExperimentalAtomicApi"),
    marker("androidx.compose.runtime.ExperimentalComposeApi"),
    marker("androidx.compose.runtime.ExperimentalComposeRuntimeApi"),
    marker("kotlin.ExperimentalContextParameters"),
    marker("kotlin.contracts.ExperimentalContracts"),
    marker("kotlinx.coroutines.ExperimentalCoroutinesApi"),
    marker("androidx.compose.animation.core.ExperimentalDeferredTransitionApi"),
    marker("kotlin.io.encoding.ExperimentalEncodingApi"),
    marker("kotlin.contracts.ExperimentalExtendedContracts"),
    marker("androidx.compose.foundation.layout.ExperimentalFlexBoxApi"),
    marker("kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi"),
    marker("kotlinx.cinterop.ExperimentalForeignApi", NATIVE),
    marker("androidx.compose.foundation.ExperimentalFoundationApi"),
    marker("androidx.compose.foundation.style.ExperimentalFoundationStyleApi"),
    marker("androidx.compose.foundation.layout.ExperimentalGridApi"),
    marker("kotlin.js.ExperimentalJsCollectionsApi"),
    marker("kotlin.js.ExperimentalJsExport"),
    marker("kotlin.js.ExperimentalJsFileName"),
    marker("kotlin.js.ExperimentalJsNoRuntime"),
    marker("kotlin.js.ExperimentalJsReflectionCreateInstance"),
    marker("kotlin.js.ExperimentalJsStatic"),
    marker("androidx.compose.foundation.layout.ExperimentalLayoutApi"),
    marker("androidx.compose.animation.ExperimentalLookaheadAnimationVisualDebugApi"),
    marker("androidx.compose.material3.ExperimentalMaterial3Api"),
    marker("androidx.compose.material.ExperimentalMaterialApi"),
    marker("kotlin.ExperimentalMultiplatform"),
    marker("kotlin.experimental.ExperimentalNativeApi"),
    marker("kotlin.experimental.ExperimentalObjCEnum"),
    marker("kotlin.experimental.ExperimentalObjCName"),
    marker("kotlin.experimental.ExperimentalObjCRefinement"),
    marker("kotlin.io.path.ExperimentalPathApi", JVM),
    marker("kotlinx.serialization.ExperimentalSerializationApi"),
    marker("androidx.compose.animation.ExperimentalSharedTransitionApi"),
    marker("kotlin.ExperimentalStdlibApi"),
    marker("kotlin.coroutines.ExperimentalStdlibCoroutineSupportApi"),
    marker("kotlin.ExperimentalSubclassOptIn"),
    marker("androidx.compose.ui.text.ExperimentalTextApi"),
    marker("kotlin.time.ExperimentalTime"),
    marker("androidx.compose.animation.core.ExperimentalTransitionApi"),
    marker("kotlin.experimental.ExperimentalTypeInference"),
    marker("kotlin.ExperimentalUnsignedTypes"),
    marker("kotlin.uuid.ExperimentalUuidApi"),
    marker("androidx.compose.ui.input.pointer.util.ExperimentalVelocityTrackerApi"),
    marker("kotlin.ExperimentalVersionOverloading"),
    marker("kotlin.wasm.ExperimentalWasmInterop", WASM_JS, WASM_WASI),
    marker("kotlin.js.ExperimentalWasmJsInterop", NATIVE, WASM_JS),
    marker("kotlinx.coroutines.FlowPreview"),
    marker("kotlinx.datetime.format.FormatStringsInDatetimeFormats"),
    marker("kotlin.native.FreezingIsDeprecated"),
    marker("androidx.compose.animation.core.InternalAnimationApi"),
    marker("androidx.compose.runtime.InternalComposeApi"),
    marker("androidx.compose.runtime.InternalComposeTracingApi"),
    marker("androidx.compose.ui.node.InternalCoreApi"),
    marker("kotlinx.coroutines.InternalCoroutinesApi"),
    marker("kotlinx.coroutines.InternalForInheritanceCoroutinesApi"),
    marker("androidx.compose.foundation.InternalFoundationApi"),
    marker("androidx.compose.foundation.text.InternalFoundationTextApi"),
    marker("kotlinx.serialization.InternalSerializationApi"),
    marker("androidx.compose.ui.text.InternalTextApi"),
    marker("kotlin.native.runtime.NativeRuntimeApi", NATIVE),
    marker("kotlinx.coroutines.ObsoleteCoroutinesApi"),
    marker("kotlin.native.ObsoleteNativeApi", NATIVE),
    marker("kotlin.native.concurrent.ObsoleteWorkersApi", NATIVE, WASM_JS, WASM_WASI),
    marker("kotlinx.serialization.SealedSerializationApi"),
    marker("kotlinx.cinterop.UnsafeNumber", NATIVE),
    marker("kotlin.wasm.unsafe.UnsafeWasmMemoryApi", WASM_JS, WASM_WASI),
)

private val MARKERS_BY_NAME = MARKERS.associateBy { it.name }

private val MARKERS_BY_SHORT_NAME = MARKERS.associateBy { it.name.substringAfterLast('.') }
