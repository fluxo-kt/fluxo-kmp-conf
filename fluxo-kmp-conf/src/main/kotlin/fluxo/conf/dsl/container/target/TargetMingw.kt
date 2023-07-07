package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import fluxo.conf.kmp.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public interface TargetMingw<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        public fun mingw(
            action: TargetMingw<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        ) {
            mingwX64(action = action)
        }


        public fun mingwX64(
            targetName: String = "mingwX64",
            action: TargetMingw<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        )

        @Deprecated(DEPRECATED_TARGET_MSG)
        public fun mingwX86(
            targetName: String = "mingwX86",
            action: TargetMingw<KotlinNativeTarget>.() -> Unit = EMPTY_FUN,
        )
    }
}
