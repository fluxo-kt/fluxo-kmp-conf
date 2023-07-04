package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import iosCompat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

public interface TargetAppleIos<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.ios
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see iosCompat
         */
        @Suppress("MaxLineLength")
        public fun ios(action: TargetAppleIos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN) {
            iosArm64(action = action)
            iosX64(action = action)
            iosSimulatorArm64(action = action)
        }


        @Deprecated(message = DEPRECATED_TARGET_MSG)
        public fun iosArm32(
            targetName: String = "iosArm32",
            action: TargetAppleIos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun iosArm64(
            targetName: String = "iosArm64",
            action: TargetAppleIos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun iosX64(
            targetName: String = "iosX64",
            action: TargetAppleIos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )

        public fun iosSimulatorArm64(
            targetName: String = "iosSimulatorArm64",
            action: TargetAppleIos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )
    }
}
