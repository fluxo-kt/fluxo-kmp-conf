package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import macosCompat
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public interface AppleMacosTarget : KotlinTargetContainer<KotlinNativeTargetWithHostTests> {

    public interface Configure {

        /**
         *
         * @see macosCompat
         */
        public fun macos(action: AppleMacosTarget.() -> Unit = EMPTY_FUN) {
            macosArm64(action = action)
            macosX64(action = action)
        }


        public fun macosArm64(
            targetName: String = "macosArm64",
            action: AppleMacosTarget.() -> Unit = EMPTY_FUN,
        )

        public fun macosX64(
            targetName: String = "macosX64",
            action: AppleMacosTarget.() -> Unit = EMPTY_FUN,
        )
    }
}
