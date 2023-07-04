@file:Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetMingw
import fluxo.conf.target.KmpTargetCode
import fluxo.conf.target.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests as KNTHT

internal abstract class TargetMingwContainer<T : KNT>(
    context: ContainerContext, name: String, code: KmpTargetCode,
) : KotlinTargetContainerImpl<T>(
    context, name, code, MINGW_SORT_ORDER,
), KotlinTargetContainerImpl.NonJvm.Native.Mingw<T>, TargetMingw<T> {

    interface Configure : TargetMingw.Configure, ContainerHolderAware {

        override fun mingwX64(targetName: String, action: TargetMingw<KNTHT>.() -> Unit) {
            holder.configure(targetName, ::X64, action)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun mingwX86(targetName: String, action: TargetMingw<KNT>.() -> Unit) {
            holder.configure(targetName, ::X86, action)
        }
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetMingwContainer<KNTHT>(context, targetName, KmpTargetCode.MINGW_X64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::mingwX64)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class X86(context: ContainerContext, targetName: String) :
        TargetMingwContainer<KNT>(context, targetName, KmpTargetCode.MINGW_X86) {

        @Suppress("DEPRECATION")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::mingwX86)
    }


    internal companion object {
        internal const val MINGW = "mingw"
    }
}
