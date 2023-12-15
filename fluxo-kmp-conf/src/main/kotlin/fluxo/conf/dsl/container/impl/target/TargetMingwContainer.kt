@file:Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
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

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun mingwX86(targetName: String, configure: MingwTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::X86, KmpTargetCode.MINGW_X86, configure)
        }
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetMingwContainer<KNTHT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::mingwX64)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class X86(context: ContainerContext, targetName: String) :
        TargetMingwContainer<KNT>(context, targetName) {

        @Suppress("DEPRECATION", "DEPRECATION_ERROR", "KotlinRedundantDiagnosticSuppress")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::mingwX86)
    }
}
