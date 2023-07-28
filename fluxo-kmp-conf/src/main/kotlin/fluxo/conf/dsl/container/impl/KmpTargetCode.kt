package fluxo.conf.dsl.container.impl

import fluxo.conf.FluxoKmpConfContext
import fluxo.conf.impl.w
import isSplitTargetsEnabled
import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.plugin.KotlinTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.konan.target.Family
import org.jetbrains.kotlin.konan.target.KonanTarget
import requestedKmpTargets

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
    WASM,

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


        private val ALL = KmpTargetCode.values()

        private val COMMON_JVM = arrayOf(JVM, ANDROID)
        private val COMMON_JS = arrayOf(JS, WASM)

        private val IOS = arrayOf(IOS_ARM32, IOS_ARM64, IOS_SIMULATOR_ARM64, IOS_X64)
        private val MACOS = arrayOf(MACOS_ARM64, MACOS_X64)
        private val OSX = MACOS
        private val TVOS = arrayOf(TVOS_ARM64, TVOS_SIMULATOR_ARM64, TVOS_X64)
        private val WATCHOS = arrayOf(
            WATCHOS_ARM32, WATCHOS_ARM64, WATCHOS_DEVICE_ARM64,
            WATCHOS_SIMULATOR_ARM64, WATCHOS_X64, WATCHOS_X86,
        )
        internal val APPLE = IOS + MACOS + TVOS + WATCHOS

        internal val LINUX =
            arrayOf(LINUX_X64, LINUX_ARM64, LINUX_ARM32_HFP, LINUX_MIPS32, LINUX_MIPSEL32)
        internal val MINGW = arrayOf(MINGW_X64, MINGW_X86)
        private val UNIX = APPLE + LINUX
        private val ANDROID_NATIVE =
            arrayOf(ANDROID_ARM32, ANDROID_ARM64, ANDROID_X64, ANDROID_X86)
        private val NATIVE = UNIX + MINGW + ANDROID_NATIVE + WASM32

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
            ::OSX.name to OSX,
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

        private val POSSIBLE_KEYS = ALIASES.keys.toList() + SPLIT_TARGETS_PROP.uppercase()


        @Throws(GradleException::class)
        @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "NestedBlockDepth")
        internal fun FluxoKmpConfContext.getSetOfRequestedKmpTargets(): Set<KmpTargetCode> {
            val project = rootProject
            val targets = project.requestedKmpTargets()
            val sequence = targets?.splitToSequence(',')

            // TODO: Support "metadata_only"/metadataOnly mode
            val isSplitTargetsEnabled = project.isSplitTargetsEnabled()
                || SPLIT_TARGETS_PROP.equals(targets, ignoreCase = true)
            if ((sequence == null || targets.isEmpty()) && !isSplitTargetsEnabled) {
                return emptySet()
            }

            val set = HashSet<KmpTargetCode>(mapCapacity(ALL.size))
            when {
                // old "split_targets"/splitTargets mode compatibility
                sequence == null || isSplitTargetsEnabled -> {
                    set.addAll(PLATFORM)

                    // On CI Mac is the target platform for generic builds, Linux for local builds.
                    val isGenericEnabled = PLATFORM === if (isCI) APPLE else LINUX
                    if (isGenericEnabled) {
                        set.addAll(COMMON_JVM)
                        set.addAll(COMMON_JS)
                    }
                }

                else -> for (v in sequence) {
                    val target = v.uppercase().trim()
                    val group = ALIASES[target]
                    if (group != null) {
                        set.addAll(group)
                    } else {
                        try {
                            set.add(valueOf(target))
                        } catch (e: IllegalArgumentException) {
                            throw GradleException(
                                "$KMP_TARGETS_PROP property of '$target' not recognized. \n" +
                                    "Known options are: $POSSIBLE_KEYS",
                                e,
                            )
                        }
                    }
                }
            }

            if (set.isNotEmpty()) {
                set.add(COMMON)
            }

            return set.optimizeReadOnlySet()
        }

        @Suppress("CyclomaticComplexMethod")
        internal fun fromKotlinTarget(target: KotlinTarget, logger: Logger?): KmpTargetCode? {
            return when (val platformType = target.platformType) {
                KotlinPlatformType.common -> COMMON
                KotlinPlatformType.jvm -> JVM
                KotlinPlatformType.androidJvm -> ANDROID
                KotlinPlatformType.js -> JS
                KotlinPlatformType.wasm -> WASM
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
