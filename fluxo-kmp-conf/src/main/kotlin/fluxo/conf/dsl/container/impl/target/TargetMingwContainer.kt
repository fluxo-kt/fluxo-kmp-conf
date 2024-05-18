@file:Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.MingwTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests as KNTHT

internal abstract class TargetMingwContainer<T : KNT>(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<T>(context, name, MINGW_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Mingw<T>,
    MingwTarget<T> {

    interface Configure : MingwTarget.Configure, ContainerHolderAware {

        override fun mingwX64(targetName: String, configure: MingwTarget<KNTHT>.() -> Unit) {
            holder.configure(targetName, ::X64, KmpTargetCode.MINGW_X64, configure)
        }
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetMingwContainer<KNTHT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::mingwX64)
    }
}
