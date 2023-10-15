@file:Suppress("DeprecatedCallableAddReplaceWith")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import fluxo.conf.dsl.container.impl.KmpTargetCode.IOS_SIMULATOR_ARM64
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AppleIosTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTS

internal abstract class TargetAppleIosContainer<T : KNT>(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<T>(context, name, APPLE_IOS_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Unix.Apple.Ios<T>, AppleIosTarget<T> {

    interface Configure : AppleIosTarget.Configure, ContainerHolderAware {

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun iosArm32(targetName: String, action: AppleIosTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm32, KmpTargetCode.IOS_ARM32, action)
        }

        override fun iosArm64(targetName: String, action: AppleIosTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.IOS_ARM64, action)
        }

        override fun iosX64(targetName: String, action: AppleIosTarget<KNTS>.() -> Unit) {
            holder.configure(targetName, ::X64, KmpTargetCode.IOS_X64, action)
        }

        override fun iosSimulatorArm64(
            targetName: String,
            action: AppleIosTarget<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::SimulatorArm64, IOS_SIMULATOR_ARM64, action)
        }
    }


    @Deprecated(DEPRECATED_TARGET_MSG)
    class Arm32(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNT>(context, targetName) {

        @Suppress("DEPRECATION", "DEPRECATION_ERROR")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosArm32)
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
