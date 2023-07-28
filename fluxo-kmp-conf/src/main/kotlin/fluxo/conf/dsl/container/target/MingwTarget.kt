package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public interface MingwTarget<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        public fun mingw(
            action: MingwTarget<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        ) {
            mingwX64(action = action)
        }


        public fun mingwX64(
            targetName: String = "mingwX64",
            action: MingwTarget<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun mingwX86(
            targetName: String = "mingwX86",
            action: MingwTarget<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )
    }
}
