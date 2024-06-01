package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl.NonJvm.Native.Nix.AndroidNative.Companion.ANDROID_NATIVE
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public interface AndroidNativeTarget : KotlinTargetContainer<KotlinNativeTarget> {

    public interface Configure {

        public fun androidNative(configure: AndroidNativeTarget.() -> Unit = EMPTY_FUN) {
            androidNativeArm32(configure = configure)
            androidNativeArm64(configure = configure)
            androidNativeX86(configure = configure)
            androidNativeX64(configure = configure)
        }

        public fun androidNative64(configure: AndroidNativeTarget.() -> Unit = EMPTY_FUN) {
            androidNativeArm64(configure = configure)
            androidNativeX64(configure = configure)
        }


        public fun androidNativeArm64(
            targetName: String = "${ANDROID_NATIVE}Arm64",
            configure: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeArm32(
            targetName: String = "${ANDROID_NATIVE}Arm32",
            configure: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeX64(
            targetName: String = "${ANDROID_NATIVE}X64",
            configure: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeX86(
            targetName: String = "${ANDROID_NATIVE}X86",
            configure: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )
    }
}
