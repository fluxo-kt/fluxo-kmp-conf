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

    MINGW_X64,

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
    WATCHOS_X64,

    ANDROID_ARM32,
    ANDROID_ARM64,
    ANDROID_X86,
    ANDROID_X64,
    ;


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

        internal val IOS = arrayOf(IOS_ARM64, IOS_SIMULATOR_ARM64, IOS_X64)
        internal val MACOS = arrayOf(MACOS_ARM64, MACOS_X64)
        internal val OSX = MACOS
        internal val TVOS = arrayOf(TVOS_ARM64, TVOS_SIMULATOR_ARM64, TVOS_X64)
        internal val WATCHOS = arrayOf(
            WATCHOS_ARM32,
            WATCHOS_ARM64,
            WATCHOS_DEVICE_ARM64,
            WATCHOS_SIMULATOR_ARM64,
            WATCHOS_X64,
        )
        internal val APPLE = IOS + MACOS + TVOS + WATCHOS

        internal val LINUX =
            arrayOf(LINUX_X64, LINUX_ARM64, LINUX_ARM32_HFP)
        internal val MINGW = arrayOf(MINGW_X64)
        internal val UNIX = APPLE + LINUX
        internal val ANDROID_NATIVE =
            arrayOf(ANDROID_ARM32, ANDROID_ARM64, ANDROID_X64, ANDROID_X86)
        internal val NATIVE = UNIX + MINGW + ANDROID_NATIVE

        internal val NON_JVM = COMMON_JS + NATIVE

        /**
         * Native codes are named after Kotlin's target names (`watchos_arm32` → [WATCHOS_ARM32]),
         * so they are matched by name. Never by `KonanTarget` object: Kotlin deletes the object of
         * a removed target (`WATCHOS_ARM32` in 2.5), and a reference to it then fails to link.
         */
        private fun KonanTarget.toKmpTargetCode(): KmpTargetCode? =
            NATIVE.firstOrNull { it.name.equals(name, ignoreCase = true) }

        internal val PLATFORM = OperatingSystem.current().let { os ->
            when {
                os.isMacOsX -> APPLE
                os.isWindows -> MINGW
                else -> LINUX
            }
        }

        // REDUNDANT_ELSE_IN_WHEN: else branches are kept intentionally — the plugin is a
        // published binary that consumers run against future KGP versions. New KonanTarget /
        // KotlinPlatformType / Family entries added by JetBrains must degrade gracefully
        // (log + return null/empty) rather than throw NoWhenBranchMatchedException.
        @Suppress("CyclomaticComplexMethod", "REDUNDANT_ELSE_IN_WHEN")
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
                    val konanTarget = (target as KotlinNativeTarget).konanTarget
                    konanTarget.toKmpTargetCode()
                        ?: null.also { logger?.w("Unexpected KonanTarget: $konanTarget") }
                }

                else -> {
                    logger?.w("Unexpected KotlinPlatformType: $platformType")
                    null
                }
            }
        }

        @Suppress("REDUNDANT_ELSE_IN_WHEN")
        internal fun fromKotlinFamily(family: Family): Array<KmpTargetCode> {
            return when (family) {
                Family.ANDROID -> ANDROID_NATIVE
                Family.MINGW -> MINGW
                Family.LINUX -> LINUX
                Family.OSX -> OSX
                Family.IOS -> IOS
                Family.TVOS -> TVOS
                Family.WATCHOS -> WATCHOS

                else -> arrayOf()
            }
        }
    }
}

/**
 * How the consumer's Kotlin supports a target. [DEPRECATED] still builds, with a warning;
 * [UNSUPPORTED] can't be built at all: removed, or deprecated past what Kotlin tolerates.
 */
internal enum class KotlinSupport { FULL, DEPRECATED, UNSUPPORTED }

/**
 * Read from the consumer's Kotlin, never from a list in the plugin: Kotlin deprecates native
 * targets, stops tolerating them (an error from then on) and later removes them, and each
 * release moves some. Non-native targets are always [KotlinSupport.FULL] here. Looked up by
 * name: Kotlin deletes the `KonanTarget` object of a removed target, and a reference to it would
 * fail to link.
 */
internal fun KmpTargetCode.kotlinSupport(): KotlinSupport {
    val native = this in KmpTargetCode.NATIVE
    val target = if (native) KonanTarget.predefinedTargets[name.lowercase()] else null
    return when {
        !native -> KotlinSupport.FULL
        target == null || target in KonanTarget.deprecatedTargets &&
            target !in KonanTarget.toleratedDeprecatedTargets -> KotlinSupport.UNSUPPORTED
        target in KonanTarget.deprecatedTargets -> KotlinSupport.DEPRECATED
        else -> KotlinSupport.FULL
    }
}
