@file:Suppress("DeprecatedCallableAddReplaceWith")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.IOS_SIMULATOR_ARM64
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AppleIosTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTS

internal abstract class TargetAppleIosContainer<T : KNT>(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<T>(context, name, APPLE_IOS_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Ios<T>,
    AppleIosTarget<T> {

    interface Configure : AppleIosTarget.Configure, ContainerHolderAware {

        override fun iosArm64(targetName: String, configure: AppleIosTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.IOS_ARM64, configure)
        }

        override fun iosX64(targetName: String, configure: AppleIosTarget<KNTS>.() -> Unit) {
            holder.configure(targetName, ::X64, KmpTargetCode.IOS_X64, configure)
        }

        override fun iosSimulatorArm64(
            targetName: String,
            configure: AppleIosTarget<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::SimulatorArm64, IOS_SIMULATOR_ARM64, configure)
        }
    }


    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosArm64)
    }

    class SimulatorArm64(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNTS>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosSimulatorArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNTS>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosX64)
    }
}
