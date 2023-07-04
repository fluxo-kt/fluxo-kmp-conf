package fluxo.conf.target

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.internal.os.OperatingSystem
import requestedKmpTargets

internal enum class KmpTargetCode {
    JVM,
    ANDROID,

    JS,
    WASM,

    LINUX_X64,
    LINUX_ARM64,
    LINUX_ARM32HFP,
    LINUX_MIPS32,
    LINUX_MIPSEL32,

    MINGW_X86,
    MINGW_X64,

    IOS_ARM32,
    IOS_ARM64,
    IOS_X64,
    IOS_SIMULATOR_ARM64,
    MACOS_ARM64,
    MACOS_X64,
    TVOS_ARM64,
    TVOS_X64,
    TVOS_SIMULATOR_ARM64,
    WATCHOS_ARM32,
    WATCHOS_ARM64,
    WATCHOS_DEVICE_ARM64,
    WATCHOS_SIMULATOR_ARM64,
    WATCHOS_X86,
    WATCHOS_X64,

    ANDROID_ARM32,
    ANDROID_ARM64,
    ANDROID_X86,
    ANDROID_X64,

    WASM_32;


    internal companion object {
        internal const val DEPRECATED_TARGET_MSG =
            org.jetbrains.kotlin.konan.target.DEPRECATED_TARGET_MESSAGE

        internal const val KMP_TARGETS_PROP = "KMP_TARGETS"


        private val ALL = KmpTargetCode.values()

        private val COMMON_JVM = arrayOf(JVM, ANDROID)
        private val COMMON_JS = arrayOf(JS, WASM)

        private val IOS = arrayOf(IOS_ARM32, IOS_ARM64, IOS_SIMULATOR_ARM64, IOS_X64)
        private val MACOS = arrayOf(MACOS_ARM64, MACOS_X64)
        private val TVOS = arrayOf(TVOS_ARM64, TVOS_SIMULATOR_ARM64, TVOS_X64)
        private val WATCHOS = arrayOf(
            WATCHOS_ARM32, WATCHOS_ARM64, WATCHOS_DEVICE_ARM64,
            WATCHOS_SIMULATOR_ARM64, WATCHOS_X64, WATCHOS_X86,
        )
        private val APPLE = IOS + MACOS + TVOS + WATCHOS

        private val LINUX =
            arrayOf(LINUX_X64, LINUX_ARM64, LINUX_ARM32HFP, LINUX_MIPS32, LINUX_MIPSEL32)
        private val MINGW = arrayOf(MINGW_X64, MINGW_X86)
        private val UNIX = APPLE + LINUX
        private val ANDROID_NATIVE = arrayOf(ANDROID_ARM32, ANDROID_ARM64, ANDROID_X64, ANDROID_X86)
        private val NATIVE = UNIX + MINGW + ANDROID_NATIVE + WASM_32

        private val NON_JVM = COMMON_JS + NATIVE

        private val PLATFORM = OperatingSystem.current().let { os ->
            when {
                os.isMacOsX -> APPLE
                os.isWindows -> MINGW
                else -> LINUX
            }
        }

        private val ALIASES = mapOf(
            ::ALL.name to ALL,
            ::COMMON_JVM.name to COMMON_JVM,
            ::COMMON_JS.name to COMMON_JS,
            ::IOS.name to IOS,
            ::MACOS.name to MACOS,
            ::TVOS.name to TVOS,
            ::WATCHOS.name to WATCHOS,
            ::APPLE.name to APPLE,
            "DARWIN" to APPLE,
            ::LINUX.name to LINUX,
            ::MINGW.name to MINGW,
            ::UNIX.name to UNIX,
            ::ANDROID_NATIVE.name to ANDROID_NATIVE,
            ::NATIVE.name to NATIVE,
            ::NON_JVM.name to NON_JVM,
            ::PLATFORM.name to PLATFORM,
        )


        @Throws(GradleException::class)
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
        internal fun Project.getSetOfRequestedKmpTargets(): Set<KmpTargetCode> {
            val sequence = requestedKmpTargets()?.splitToSequence(',')
                ?: return emptySet()

            val set = HashSet<KmpTargetCode>(mapCapacity(ALL.size))
            for (v in sequence) {
                val target = v.uppercase().trim()
                val group = ALIASES[target]
                if (group != null) {
                    set.addAll(group)
                } else {
                    try {
                        set.add(valueOf(target))
                    } catch (e: IllegalArgumentException) {
                        throw GradleException(
                            "$KMP_TARGETS_PROP property of '$target' not recognized", e,
                        )
                    }
                }
            }
            return set.optimizeReadOnlySet()
        }
    }
}
