package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AppleMacosTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

internal abstract class TargetAppleMacosContainer(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<KotlinNativeTargetWithHostTests>(context, name, APPLE_MACOS_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Unix.Apple.Macos, AppleMacosTarget {

    interface Configure : AppleMacosTarget.Configure, ContainerHolderAware {

        override fun macosArm64(targetName: String, action: AppleMacosTarget.() -> Unit) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.MACOS_ARM64, action)
        }

        override fun macosX64(targetName: String, action: AppleMacosTarget.() -> Unit) {
            holder.configure(targetName, ::X64, KmpTargetCode.MACOS_X64, action)
        }
    }


    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleMacosContainer(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::macosArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleMacosContainer(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::macosX64)
    }
}
