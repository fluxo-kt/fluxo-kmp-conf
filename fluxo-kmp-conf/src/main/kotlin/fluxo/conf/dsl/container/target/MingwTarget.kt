package fluxo.conf.dsl.container.target

import fluxo.conf.dsl.container.KotlinTargetContainer
import fluxo.conf.impl.EMPTY_FUN
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

public interface MingwTarget<out T : KotlinNativeTarget> : KotlinTargetContainer<T> {

    public interface Configure {
        public fun mingw(
            configure: MingwTarget<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        ) {
            mingwX64(configure = configure)
        }


        public fun mingwX64(
            targetName: String = "mingwX64",
            configure: MingwTarget<KotlinNativeTargetWithHostTests>.() -> Unit = EMPTY_FUN,
        )
    }
}
