package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.kmp.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public interface TargetLinux<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        public fun linux(action: TargetLinux<KotlinNativeTarget>.() -> Unit = EMPTY_FUN) {
            linuxX64(action = action)
            linuxArm64(action = action)
        }


        public fun linuxX64(
            targetName: String = "linuxX64",
            action: TargetLinux<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        )

        public fun linuxArm64(
            targetName: String = "linuxArm64",
            action: TargetLinux<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )


        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun linuxArm32Hfp(
            targetName: String = "linuxArm32Hfp",
            action: TargetLinux<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun linuxMips32(
            targetName: String = "linuxMips32",
            action: TargetLinux<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun linuxMipsel32(
            targetName: String = "linuxMipsel32",
            action: TargetLinux<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )
    }
}
