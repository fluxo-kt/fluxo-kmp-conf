package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KotlinTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetAppleTvos
import fluxo.conf.target.KmpTargetCode
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTS
import tvosCompat

internal abstract class TargetAppleTvosContainer<T : KNT>(
    context: ContainerContext, name: String, code: KmpTargetCode,
) : KotlinTargetContainerImpl<T>(
    context, name, code, APPLE_TVOS_SORT_ORDER,
), KotlinTargetContainerImpl.NonJvm.Native.Unix.Apple.Tvos<T>, TargetAppleTvos<T> {

    interface Configure : TargetAppleTvos.Configure, ContainerHolderAware {

        override fun tvosArm64(targetName: String, action: TargetAppleTvos<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, action)
        }

        override fun tvosSimulatorArm64(
            targetName: String,
            action: TargetAppleTvos<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::SimulatorArm64, action)
        }

        override fun tvosX64(targetName: String, action: TargetAppleTvos<KNTS>.() -> Unit) {
            holder.configure(targetName, ::X64, action)
        }
    }


    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleTvosContainer<KNT>(context, targetName, KmpTargetCode.TVOS_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::tvosArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleTvosContainer<KNTS>(context, targetName, KmpTargetCode.TVOS_X64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::tvosX64)
    }

    class SimulatorArm64(context: ContainerContext, targetName: String) :
        TargetAppleTvosContainer<KNTS>(context, targetName, KmpTargetCode.TVOS_SIMULATOR_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::tvosSimulatorArm64)
    }


    internal companion object {
        internal const val TVOS = "tvos"
    }
}
