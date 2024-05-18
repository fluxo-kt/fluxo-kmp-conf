package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import iosCompat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests

public interface AppleIosTarget<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.ios
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see iosCompat
         */
        @Suppress("MaxLineLength")
        public fun ios(configure: AppleIosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN) {
            iosArm64(configure = configure)
            iosX64(configure = configure)
            iosSimulatorArm64(configure = configure)
        }


        public fun iosArm64(
            targetName: String = "iosArm64",
            configure: AppleIosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun iosX64(
            targetName: String = "iosX64",
            configure: AppleIosTarget<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )

        public fun iosSimulatorArm64(
            targetName: String = "iosSimulatorArm64",
            configure: AppleIosTarget<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )
    }
}
