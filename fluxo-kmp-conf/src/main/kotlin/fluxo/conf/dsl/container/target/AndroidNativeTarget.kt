package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

public interface AndroidNativeTarget : KotlinTargetContainer<KotlinNativeTarget> {

    public interface Configure {

        public fun androidNative(action: AndroidNativeTarget.() -> Unit = EMPTY_FUN) {
            androidNativeArm32(action = action)
            androidNativeArm64(action = action)
            androidNativeX86(action = action)
            androidNativeX64(action = action)
        }

        public fun androidNative64(action: AndroidNativeTarget.() -> Unit = EMPTY_FUN) {
            androidNativeArm64(action = action)
            androidNativeX64(action = action)
        }


        public fun androidNativeArm64(
            targetName: String = "${ANDROID_NATIVE}Arm64",
            action: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeArm32(
            targetName: String = "${ANDROID_NATIVE}Arm32",
            action: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeX64(
            targetName: String = "${ANDROID_NATIVE}X64",
            action: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )

        public fun androidNativeX86(
            targetName: String = "${ANDROID_NATIVE}X86",
            action: AndroidNativeTarget.() -> Unit = EMPTY_FUN,
        )
    }

    private companion object {
        private const val ANDROID_NATIVE = "androidNative"
    }
}
