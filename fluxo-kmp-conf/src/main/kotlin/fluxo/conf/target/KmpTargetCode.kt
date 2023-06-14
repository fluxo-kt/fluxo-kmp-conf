package fluxo.conf.target

import fluxo.conf.dsl.container.target.KmpTarget
import fluxo.conf.dsl.container.target.TargetAndroidContainer
import fluxo.conf.dsl.container.target.TargetAndroidNativeContainer
import fluxo.conf.dsl.container.target.TargetAppleIosContainer
import fluxo.conf.dsl.container.target.TargetJsContainer
import fluxo.conf.dsl.container.target.TargetJvmContainer
import fluxo.conf.dsl.container.target.TargetLinuxContainer
import fluxo.conf.dsl.container.target.TargetAppleMacosContainer
import fluxo.conf.dsl.container.target.TargetMingwContainer
import fluxo.conf.dsl.container.target.TargetAppleTvosContainer
import fluxo.conf.dsl.container.target.TargetWasmContainer
import fluxo.conf.dsl.container.target.TargetWasmNativeContainer
import fluxo.conf.dsl.container.target.TargetAppleWatchosContainer
import org.gradle.api.GradleException
import org.gradle.api.Project
import requestedKmpTargets

internal enum class KmpTargetCode {
    ANDROID,
    ANDROID_ARM32,
    ANDROID_ARM64,
    ANDROID_X64,
    ANDROID_X86,
    JVM,
    JS,

    @Deprecated(message = TARGET_DEPRECTION_MSG)
    LINUX_ARM32HFP,
    LINUX_ARM64,

    @Deprecated(message = TARGET_DEPRECTION_MSG)
    LINUX_MIPS32,

    @Deprecated(message = TARGET_DEPRECTION_MSG)
    LINUX_MIPSEL32,
    LINUX_X64,
    MINGW_X64,

    @Deprecated(message = TARGET_DEPRECTION_MSG)
    MINGW_X86,

    @Deprecated(message = TARGET_DEPRECTION_MSG)
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

    @Deprecated(message = TARGET_DEPRECTION_MSG)
    WATCHOS_X86,
    WASM,

    @Deprecated(message = TARGET_DEPRECTION_MSG)
    WASM_32;

    internal companion object {
        internal const val TARGET_DEPRECTION_MSG =
            "Target is deprecated, will be removed soon: see https://kotl.in/native-targets-tiers"

        internal const val KMP_TARGETS_PROP = "KMP_TARGETS"

        // FIXME: Add support for auto-detection of targets with "PLATFORM" value,
        //  aka "split_targets" mode
        @Throws(GradleException::class)
        internal fun Project.getSetOfRequestedKmpTargets(): Set<KmpTargetCode> {
            return requestedKmpTargets()
                ?.split(',')
                ?.map { target ->
                    try {
                        KmpTargetCode.valueOf(target.trim())
                    } catch (e: IllegalArgumentException) {
                        throw GradleException(
                            "$KMP_TARGETS_PROP property of '$target' not recognized", e,
                        )
                    }
                }
                ?.toSet()
                .orEmpty()
        }

        @Suppress("CyclomaticComplexMethod", "DEPRECATION")
        internal fun KmpTarget<*>.property(): KmpTargetCode {
            return when (this) {
                is TargetAndroidContainer<*> -> ANDROID
                is TargetAndroidNativeContainer.Arm32 -> ANDROID_ARM32
                is TargetAndroidNativeContainer.Arm64 -> ANDROID_ARM64
                is TargetAndroidNativeContainer.X64 -> ANDROID_X64
                is TargetAndroidNativeContainer.X86 -> ANDROID_X86
                is TargetJvmContainer -> JVM
                is TargetJsContainer -> JS
                is TargetLinuxContainer.Arm32Hfp -> LINUX_ARM32HFP
                is TargetLinuxContainer.Arm64 -> LINUX_ARM64
                is TargetLinuxContainer.Mips32 -> LINUX_MIPS32
                is TargetLinuxContainer.Mipsel32 -> LINUX_MIPSEL32
                is TargetLinuxContainer.X64 -> LINUX_X64
                is TargetMingwContainer.X64 -> MINGW_X64
                is TargetMingwContainer.X86 -> MINGW_X86
                is TargetAppleIosContainer.Arm32 -> IOS_ARM32
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
                is TargetAppleWatchosContainer.X86 -> WATCHOS_X86
                is TargetWasmNativeContainer -> WASM_32
                is TargetWasmContainer -> WASM
            }
        }
    }
}
