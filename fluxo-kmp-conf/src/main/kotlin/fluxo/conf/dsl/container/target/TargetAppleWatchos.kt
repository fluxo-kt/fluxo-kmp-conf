package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.kmp.KmpTargetCode
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import watchosCompat

public interface TargetAppleWatchos<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.watchos
         * @see watchosCompat
         */
        public fun watchos(action: TargetAppleWatchos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN)


        public fun watchosArm32(
            targetName: String = "watchosArm32",
            action: TargetAppleWatchos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun watchosArm64(
            targetName: String = "watchosArm64",
            action: TargetAppleWatchos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        @SinceKotlin("1.8.0")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN")
        public fun watchosDeviceArm64(
            targetName: String = "watchosDeviceArm64",
            action: TargetAppleWatchos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun watchosX64(
            targetName: String = "watchosX64",
            action: TargetAppleWatchos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(message = KmpTargetCode.DEPRECATED_TARGET_MSG)
        public fun watchosX86(
            targetName: String = "watchosX86",
            action: TargetAppleWatchos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )

        public fun watchosSimulatorArm64(
            targetName: String = "watchosSimulatorArm64",
            action: TargetAppleWatchos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )
    }
}
