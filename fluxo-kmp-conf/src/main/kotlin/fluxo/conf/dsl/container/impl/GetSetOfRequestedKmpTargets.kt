package fluxo.conf.dsl.container.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion as Code
import isSplitTargetsEnabled
import org.gradle.api.GradleException
import requestedKmpTargets

@Throws(GradleException::class)
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NestedBlockDepth")
internal fun FluxoKmpConfContext.getSetOfRequestedKmpTargets(): Set<KmpTargetCode> {
    val project = rootProject
    val targets = project.requestedKmpTargets()
    val sequence = targets?.splitToSequence(',')

    // TODO: Support "metadata_only"/metadataOnly mode (see arkivanov/gradle-setup-plugin)
    val isSplitTargetsEnabled = project.isSplitTargetsEnabled()
        || Code.SPLIT_TARGETS_PROP.equals(targets, ignoreCase = true)
    if ((sequence == null || targets.isEmpty()) && !isSplitTargetsEnabled) {
        return emptySet()
    }

    val set = HashSet<KmpTargetCode>(mapCapacity(Code.ALL.size))
    when {
        // old "split_targets"/splitTargets mode compatibility
        sequence == null || isSplitTargetsEnabled -> {
            set.addAll(Code.PLATFORM)

            // On CI Mac is the target platform for generic builds, Linux for local builds.
            val isGenericEnabled =
                Code.PLATFORM === if (isCI) Code.APPLE else Code.LINUX
            if (isGenericEnabled) {
                set.addAll(Code.COMMON_JVM)
                set.addAll(Code.COMMON_JS)
            }
        }

        else -> for (v in sequence) {
            val target = v.uppercase().trim()
            val group = ALIASES[target]
            if (group != null) {
                set.addAll(group)
            } else {
                try {
                    set.add(KmpTargetCode.valueOf(target))
                } catch (e: IllegalArgumentException) {
                    throw GradleException(
                        "${Code.KMP_TARGETS_PROP} property of '$target' not recognized. \n" +
                            "Known options are: $POSSIBLE_KEYS",
                        e,
                    )
                }
            }
        }
    }

    if (set.isNotEmpty()) {
        set.add(KmpTargetCode.COMMON)
    }

    return set.optimizeReadOnlySet()
}


private val ALIASES = mapOf(
    Code::ALL.name to Code.ALL,
    Code::COMMON_JVM.name to Code.COMMON_JVM,
    Code::COMMON_WASM.name to Code.COMMON_WASM,
    "WASM" to arrayOf(KmpTargetCode.WASM_JS),
    Code::COMMON_JS.name to Code.COMMON_JS,
    Code::IOS.name to Code.IOS,
    Code::OSX.name to Code.OSX,
    Code::MACOS.name to Code.MACOS,
    Code::TVOS.name to Code.TVOS,
    Code::WATCHOS.name to Code.WATCHOS,
    Code::APPLE.name to Code.APPLE,
    "DARWIN" to Code.APPLE,
    Code::LINUX.name to Code.LINUX,
    Code::MINGW.name to Code.MINGW,
    Code::UNIX.name to Code.UNIX,
    Code::ANDROID_NATIVE.name to Code.ANDROID_NATIVE,
    Code::NATIVE.name to Code.NATIVE,
    Code::NON_JVM.name to Code.NON_JVM,
    Code::PLATFORM.name to Code.PLATFORM,
)

private val POSSIBLE_KEYS = ALIASES.keys.toList() + Code.SPLIT_TARGETS_PROP.uppercase()
