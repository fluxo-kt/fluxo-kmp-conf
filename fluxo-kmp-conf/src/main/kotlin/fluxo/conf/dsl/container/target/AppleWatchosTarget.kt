package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTST
import watchosCompat

public interface AppleWatchosTarget<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.watchos
         * @see watchosCompat
         */
        public fun watchos(configure: AppleWatchosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN)


        public fun watchosArm32(
            targetName: String = "watchosArm32",
            configure: AppleWatchosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun watchosArm64(
            targetName: String = "watchosArm64",
            configure: AppleWatchosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        @SinceKotlin("1.8.0")
        @Suppress("NEWER_VERSION_IN_SINCE_KOTLIN", "KotlinRedundantDiagnosticSuppress")
        public fun watchosDeviceArm64(
            targetName: String = "watchosDeviceArm64",
            configure: AppleWatchosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun watchosX64(
            targetName: String = "watchosX64",
            configure: AppleWatchosTarget<KNTST>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun watchosX86(
            targetName: String = "watchosX86",
            configure: AppleWatchosTarget<KNTST>.() -> Unit = EMPTY_FUN,
        )

        public fun watchosSimulatorArm64(
            targetName: String = "watchosSimulatorArm64",
            configure: AppleWatchosTarget<KNTST>.() -> Unit = EMPTY_FUN,
        )
    }
}
