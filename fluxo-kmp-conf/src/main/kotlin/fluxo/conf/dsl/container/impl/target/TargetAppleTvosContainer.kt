package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.TVOS_SIMULATOR_ARM64
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AppleTvosTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTS

internal abstract class TargetAppleTvosContainer<T : KNT>(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<T>(context, name, APPLE_TVOS_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Unix.Apple.Tvos<T>, AppleTvosTarget<T> {

    interface Configure : AppleTvosTarget.Configure, ContainerHolderAware {

        override fun tvosArm64(targetName: String, action: AppleTvosTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.TVOS_ARM64, action)
        }

        override fun tvosSimulatorArm64(
            targetName: String,
            action: AppleTvosTarget<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::SimulatorArm64, TVOS_SIMULATOR_ARM64, action)
        }

        override fun tvosX64(targetName: String, action: AppleTvosTarget<KNTS>.() -> Unit) {
            holder.configure(targetName, ::X64, KmpTargetCode.TVOS_X64, action)
        }
    }


    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleTvosContainer<KNT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::tvosArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleTvosContainer<KNTS>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::tvosX64)
    }

    class SimulatorArm64(context: ContainerContext, targetName: String) :
        TargetAppleTvosContainer<KNTS>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::tvosSimulatorArm64)
    }
}
