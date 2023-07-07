@file:Suppress("DeprecatedCallableAddReplaceWith")

package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.TargetAppleIos
import fluxo.conf.kmp.KmpTargetCode
import fluxo.conf.kmp.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTS

internal abstract class TargetAppleIosContainer<T : KNT>(
    context: ContainerContext, name: String, code: KmpTargetCode,
) : KmpTargetContainerImpl<T>(
    context, name, code, APPLE_IOS_SORT_ORDER,
), KmpTargetContainerImpl.NonJvm.Native.Unix.Apple.Ios<T>, TargetAppleIos<T> {

    interface Configure : TargetAppleIos.Configure, ContainerHolderAware {

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun iosArm32(targetName: String, action: TargetAppleIos<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm32, action)
        }

        override fun iosArm64(targetName: String, action: TargetAppleIos<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, action)
        }

        override fun iosX64(targetName: String, action: TargetAppleIos<KNTS>.() -> Unit) {
            holder.configure(targetName, ::X64, action)
        }

        override fun iosSimulatorArm64(
            targetName: String,
            action: TargetAppleIos<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::SimulatorArm64, action)
        }
    }


    @Deprecated(DEPRECATED_TARGET_MSG)
    class Arm32(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNT>(context, targetName, KmpTargetCode.IOS_ARM32) {

        @Suppress("DEPRECATION")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosArm32)
    }

    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNT>(context, targetName, KmpTargetCode.IOS_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosArm64)
    }

    class SimulatorArm64(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNTS>(context, targetName, KmpTargetCode.IOS_SIMULATOR_ARM64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosSimulatorArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleIosContainer<KNTS>(context, targetName, KmpTargetCode.IOS_X64) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::iosX64)
    }


    internal companion object {
        internal const val IOS = "ios"
    }
}
