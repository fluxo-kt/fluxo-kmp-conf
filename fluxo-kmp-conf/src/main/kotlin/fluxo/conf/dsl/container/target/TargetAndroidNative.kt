package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public interface TargetAndroidNative : KotlinTargetContainer<KotlinNativeTarget> {

    public interface Configure {

        public fun androidNative(action: TargetAndroidNative.() -> Unit = EMPTY_FUN) {
            androidNativeArm32(action = action)
            androidNativeArm64(action = action)
            androidNativeX86(action = action)
            androidNativeX64(action = action)
        }

        public fun androidNative64(action: TargetAndroidNative.() -> Unit = EMPTY_FUN) {
            androidNativeArm64(action = action)
            androidNativeX64(action = action)
        }


        public fun androidNativeArm64(
            targetName: String = "androidNativeArm64",
            action: TargetAndroidNative.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeArm32(
            targetName: String = "androidNativeArm32",
            action: TargetAndroidNative.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeX64(
            targetName: String = "androidNativeX64",
            action: TargetAndroidNative.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeX86(
            targetName: String = "androidNativeX86",
            action: TargetAndroidNative.() -> Unit = EMPTY_FUN,
        )
    }
}
