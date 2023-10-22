package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public interface LinuxTarget<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        public fun linux(configure: LinuxTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN) {
            linuxX64(configure = configure)
            linuxArm64(configure = configure)
        }


        public fun linuxX64(
            targetName: String = "linuxX64",
            configure: LinuxTarget<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        )

        public fun linuxArm64(
            targetName: String = "linuxArm64",
            configure: LinuxTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )


        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun linuxArm32Hfp(
            targetName: String = "linuxArm32Hfp",
            configure: LinuxTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun linuxMips32(
            targetName: String = "linuxMips32",
            configure: LinuxTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun linuxMipsel32(
            targetName: String = "linuxMipsel32",
            configure: LinuxTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )
    }
}
