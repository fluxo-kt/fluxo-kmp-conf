package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetAppleMacos
import fluxo.conf.target.KmpTargetCode
import macosCompat
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests

internal abstract class TargetAppleMacosContainer(
    context: ContainerContext, name: String, code: KmpTargetCode,
) : KotlinTargetContainerImpl<KotlinNativeTargetWithHostTests>(
    context, name, code, APPLE_MACOS_SORT_ORDER,
), KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.Macos, TargetAppleMacos {

    interface Configure : TargetAppleMacos.Configure, ContainerHolderAware {

        override fun macosArm64(targetName: String, action: TargetAppleMacos.() -> Unit) {
            holder.configure(targetName, ::Arm64, action)
        }

        override fun macosX64(targetName: String, action: TargetAppleMacos.() -> Unit) {
            holder.configure(targetName, ::X64, action)
        }
    }


    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleMacosContainer(context, targetName, KmpTargetCode.MACOS_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::macosArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleMacosContainer(context, targetName, KmpTargetCode.MACOS_X64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::macosX64)
    }


    internal companion object {
        internal const val MACOS = "macos"
    }
}
