package fluxo.conf.dsl.container.impl

import fluxo.log.w
import org.gradle.api.logging.Logger
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.targets.js.dsl.KotlinWasmWasiTargetDsl
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget

/**
 *
 * @see org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
 * @see org.jetbrains.kotlin.konan.target.Family
 * @see org.jetbrains.kotlin.konan.target.KonanTarget
 */
internal enum class KmpTargetCode {
    COMMON,

    JVM,

    /** Android/JVM */
    ANDROID,

    JS,
    WASM_JS,
    WASM_WASI,

    LINUX_X64,
    LINUX_ARM64,
    LINUX_ARM32_HFP,
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

    WASM32;


    internal companion object {
        internal const val DEPRECATED_TARGET_MSG =
            org.jetbrains.kotlin.konan.target.DEPRECATED_TARGET_MESSAGE

        internal const val KMP_TARGETS_PROP = "KMP_TARGETS"
        internal const val KMP_TARGETS_ALL_PROP = "KMP_TARGETS_ALL"
        internal const val SPLIT_TARGETS_PROP = "split_targets"


        internal val ALL = KmpTargetCode.values()

        internal val COMMON_JVM = arrayOf(JVM, ANDROID)
        internal val COMMON_WASM = arrayOf(WASM_JS, WASM_WASI)
        internal val COMMON_JS = COMMON_WASM + JS

        internal val IOS = arrayOf(IOS_ARM32, IOS_ARM64, IOS_SIMULATOR_ARM64, IOS_X64)
        internal val MACOS = arrayOf(MACOS_ARM64, MACOS_X64)
        internal val OSX = MACOS
        internal val TVOS = arrayOf(TVOS_ARM64, TVOS_SIMULATOR_ARM64, TVOS_X64)
        internal val WATCHOS = arrayOf(
            WATCHOS_ARM32,
            WATCHOS_ARM64,
            WATCHOS_DEVICE_ARM64,
            WATCHOS_SIMULATOR_ARM64,
            WATCHOS_X64,
            WATCHOS_X86,
        )
        internal val APPLE = IOS + MACOS + TVOS + WATCHOS

        internal val LINUX =
            arrayOf(LINUX_X64, LINUX_ARM64, LINUX_ARM32_HFP, LINUX_MIPS32, LINUX_MIPSEL32)
        internal val MINGW = arrayOf(MINGW_X64, MINGW_X86)
        internal val UNIX = APPLE + LINUX
        internal val ANDROID_NATIVE =
            arrayOf(ANDROID_ARM32, ANDROID_ARM64, ANDROID_X64, ANDROID_X86)
        internal val NATIVE = UNIX + MINGW + ANDROID_NATIVE + WASM32

        internal val NON_JVM = COMMON_JS + NATIVE

        internal val PLATFORM = OperatingSystem.current().let { os ->
            when {
                os.isMacOsX -> APPLE
                os.isWindows -> MINGW
                else -> LINUX
            }
        }

        @Suppress("CyclomaticComplexMethod")
        internal fun fromKotlinTarget(target: KotlinTarget, logger: Logger?): KmpTargetCode? {
            return when (val platformType = target.platformType) {
                KotlinPlatformType.common -> COMMON
                KotlinPlatformType.jvm -> JVM
                KotlinPlatformType.androidJvm -> ANDROID
                KotlinPlatformType.js -> JS

                KotlinPlatformType.wasm -> {
                    try {
                        // Kotlin 1.9.20+
                        if (target is KotlinWasmWasiTargetDsl) {
                            WASM_WASI
                        }
                    } catch (_: Throwable) {
                    }
                    WASM_JS
                }

                KotlinPlatformType.native -> {
                    when (val konanTarget = (target as KotlinNativeTarget).konanTarget) {
                        KonanTarget.ANDROID_ARM32 -> ANDROID_ARM32
                        KonanTarget.ANDROID_ARM64 -> ANDROID_ARM64
                        KonanTarget.ANDROID_X64 -> ANDROID_X64
                        KonanTarget.ANDROID_X86 -> ANDROID_X86
                        KonanTarget.IOS_ARM32 -> IOS_ARM32
                        KonanTarget.IOS_ARM64 -> IOS_ARM64
                        KonanTarget.IOS_SIMULATOR_ARM64 -> IOS_SIMULATOR_ARM64
                        KonanTarget.IOS_X64 -> IOS_X64
                        KonanTarget.LINUX_ARM32_HFP -> LINUX_ARM32_HFP
                        KonanTarget.LINUX_ARM64 -> LINUX_ARM64
                        KonanTarget.LINUX_MIPS32 -> LINUX_MIPS32
                        KonanTarget.LINUX_MIPSEL32 -> LINUX_MIPSEL32
                        KonanTarget.LINUX_X64 -> LINUX_X64
                        KonanTarget.MACOS_ARM64 -> MACOS_ARM64
                        KonanTarget.MACOS_X64 -> MACOS_X64
                        KonanTarget.MINGW_X64 -> MINGW_X64
                        KonanTarget.MINGW_X86 -> MINGW_X86
                        KonanTarget.TVOS_ARM64 -> TVOS_ARM64
                        KonanTarget.TVOS_SIMULATOR_ARM64 -> TVOS_SIMULATOR_ARM64
                        KonanTarget.TVOS_X64 -> TVOS_X64
                        KonanTarget.WASM32 -> WASM32
                        KonanTarget.WATCHOS_ARM32 -> WATCHOS_ARM32
                        KonanTarget.WATCHOS_ARM64 -> WATCHOS_ARM64
                        KonanTarget.WATCHOS_DEVICE_ARM64 -> WATCHOS_DEVICE_ARM64
                        KonanTarget.WATCHOS_SIMULATOR_ARM64 -> WATCHOS_SIMULATOR_ARM64
                        KonanTarget.WATCHOS_X64 -> WATCHOS_X64
                        KonanTarget.WATCHOS_X86 -> WATCHOS_X86

                        else -> {
                            logger?.w("Unexpected KonanTarget: $konanTarget")
                            null
                        }
                    }
                }

                else -> {
                    logger?.w("Unexpected KotlinPlatformType: $platformType")
                    null
                }
            }
        }

        internal fun fromKotlinFamily(family: Family): Array<KmpTargetCode> {
            return when (family) {
                Family.WASM -> arrayOf(WASM32)
                Family.ANDROID -> ANDROID_NATIVE
                Family.MINGW -> MINGW
                Family.LINUX -> LINUX
                Family.OSX -> OSX
                Family.IOS -> IOS
                Family.TVOS -> TVOS
                Family.WATCHOS -> WATCHOS

                Family.ZEPHYR -> arrayOf()
            }
        }
    }
}
