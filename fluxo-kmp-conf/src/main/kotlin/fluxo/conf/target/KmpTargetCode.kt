package fluxo.conf.target

import fluxo.conf.dsl.container.target.KmpTarget
import fluxo.conf.dsl.container.target.TargetAndroidContainer
import fluxo.conf.dsl.container.target.TargetAndroidNativeContainer
import fluxo.conf.dsl.container.target.TargetAppleIosContainer
import fluxo.conf.dsl.container.target.TargetAppleMacosContainer
import fluxo.conf.dsl.container.target.TargetAppleTvosContainer
import fluxo.conf.dsl.container.target.TargetAppleWatchosContainer
import fluxo.conf.dsl.container.target.TargetJsContainer
import fluxo.conf.dsl.container.target.TargetJvmContainer
import fluxo.conf.dsl.container.target.TargetLinuxContainer
import fluxo.conf.dsl.container.target.TargetMingwContainer
import fluxo.conf.dsl.container.target.TargetWasmContainer
import fluxo.conf.dsl.container.target.TargetWasmNativeContainer
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
    IOS_SIMULATOR_ARM64,
    IOS_X64,
    MACOS_ARM64,
    MACOS_X64,
    TVOS_ARM64,
    TVOS_SIMULATOR_ARM64,
    TVOS_X64,
    WATCHOS_ARM32,
    WATCHOS_ARM64,
    WATCHOS_DEVICE_ARM64,
    WATCHOS_SIMULATOR_ARM64,
    WATCHOS_X64,
    WATCHOS_X86,

    ANDROID_ARM32,
    ANDROID_ARM64,
    ANDROID_X64,
    ANDROID_X86,

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

        @Suppress("CyclomaticComplexMethod", "DEPRECATION")
        internal fun KmpTarget<*>.property(): KmpTargetCode {
            // FIXME: lookup in map by name?
            return when (this) {
                is TargetAndroidContainer<*> -> ANDROID
                is TargetJvmContainer -> JVM
                is TargetJsContainer -> JS
                is TargetLinuxContainer.X64 -> LINUX_X64
                is TargetLinuxContainer.Arm64 -> LINUX_ARM64
                is TargetMingwContainer.X64 -> MINGW_X64
                is TargetWasmContainer -> WASM

                is TargetAppleIosContainer.Arm64 -> IOS_ARM64
                is TargetAppleIosContainer.SimulatorArm64 -> IOS_SIMULATOR_ARM64
                is TargetAppleIosContainer.X64 -> IOS_X64
                is TargetAppleMacosContainer.Arm64 -> MACOS_ARM64
                is TargetAppleMacosContainer.X64 -> MACOS_X64
                is TargetAppleTvosContainer.Arm64 -> TVOS_ARM64
                is TargetAppleTvosContainer.SimulatorArm64 -> TVOS_SIMULATOR_ARM64
                is TargetAppleTvosContainer.X64 -> TVOS_X64
                is TargetAppleWatchosContainer.Arm32 -> WATCHOS_ARM32
                is TargetAppleWatchosContainer.Arm64 -> WATCHOS_ARM64
                is TargetAppleWatchosContainer.DeviceArm64 -> WATCHOS_DEVICE_ARM64
                is TargetAppleWatchosContainer.SimulatorArm64 -> WATCHOS_SIMULATOR_ARM64
                is TargetAppleWatchosContainer.X64 -> WATCHOS_X64

                is TargetAndroidNativeContainer.Arm32 -> ANDROID_ARM32
                is TargetAndroidNativeContainer.Arm64 -> ANDROID_ARM64
                is TargetAndroidNativeContainer.X64 -> ANDROID_X64
                is TargetAndroidNativeContainer.X86 -> ANDROID_X86

                is TargetAppleIosContainer.Arm32 -> IOS_ARM32
                is TargetAppleWatchosContainer.X86 -> WATCHOS_X86
                is TargetLinuxContainer.Arm32Hfp -> LINUX_ARM32HFP
                is TargetLinuxContainer.Mips32 -> LINUX_MIPS32
                is TargetLinuxContainer.Mipsel32 -> LINUX_MIPSEL32
                is TargetMingwContainer.X86 -> MINGW_X86
                is TargetWasmNativeContainer -> WASM_32
            }
        }
    }
}
