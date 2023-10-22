package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests
import tvosCompat

public interface AppleTvosTarget<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.tvos
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see tvosCompat
         */
        @Suppress("MaxLineLength")
        public fun tvos(configure: AppleTvosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN) {
            tvosArm64(configure = configure)
            tvosX64(configure = configure)
            tvosSimulatorArm64(configure = configure)
        }


        public fun tvosArm64(
            targetName: String = "tvosArm64",
            configure: AppleTvosTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        public fun tvosX64(
            targetName: String = "tvosX64",
            configure: AppleTvosTarget<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )

        public fun tvosSimulatorArm64(
            targetName: String = "tvosSimulatorArm64",
            configure: AppleTvosTarget<KotlinNativeTargetWithSimulatorTests>.() -> Unit = EMPTY_FUN,
        )
    }
}
