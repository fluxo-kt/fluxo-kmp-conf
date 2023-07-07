package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import tvosCompat

public interface TargetAppleTvos<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.tvos
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see tvosCompat
         */
        @Suppress("MaxLineLength")
        public fun tvos(action: TargetAppleTvos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN) {
            tvosArm64(action = action)
            tvosX64(action = action)
            tvosSimulatorArm64(action = action)
        }


        public fun tvosArm64(
            targetName: String = "tvosArm64",
            action: TargetAppleTvos<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun tvosX64(
            targetName: String = "tvosX64",
            action: TargetAppleTvos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )

        public fun tvosSimulatorArm64(
            targetName: String = "tvosSimulatorArm64",
            action: TargetAppleTvos<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )
    }
}
