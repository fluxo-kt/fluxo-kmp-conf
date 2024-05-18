package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.WATCHOS_SIMULATOR_ARM64
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.AppleWatchosTarget
import fluxo.conf.impl.kotlin.KOTLIN_1_8
import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithSimulatorTests as KNTS
import watchosCompat

internal abstract class TargetAppleWatchosContainer<T : KNT>(
    context: ContainerContext,
    name: String,
) : KmpTargetContainerImpl<T>(context, name, APPLE_WATCHOS_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Nix.Apple.Watchos<T>,
    AppleWatchosTarget<T> {

    interface Configure : AppleWatchosTarget.Configure, ContainerHolderAware {

        /**
         *
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.watchos
         * @see org.jetbrains.kotlin.gradle.dsl.KotlinTargetContainerWithNativeShortcuts.createIntermediateSourceSet
         * @see watchosCompat
         */
        @Suppress("MaxLineLength")
        override fun watchos(configure: AppleWatchosTarget<KNT>.() -> Unit) {
            watchosArm32(configure = configure)
            watchosArm64(configure = configure)
            if (holder.kotlinPluginVersion >= KOTLIN_1_8) {
                watchosDeviceArm64(configure = configure)
            }
            watchosX64(configure = configure)
            watchosSimulatorArm64(configure = configure)
        }


        override fun watchosArm32(
            targetName: String,
            configure: AppleWatchosTarget<KNT>.() -> Unit,
        ) {
            holder.configure(targetName, ::Arm32, KmpTargetCode.WATCHOS_ARM32, configure)
        }

        override fun watchosArm64(
            targetName: String,
            configure: AppleWatchosTarget<KNT>.() -> Unit,
        ) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.WATCHOS_ARM64, configure)
        }

        override fun watchosDeviceArm64(
            targetName: String,
            configure: AppleWatchosTarget<KNT>.() -> Unit,
        ) {
            if (holder.kotlinPluginVersion < KOTLIN_1_8) {
                throw GradleException("watchosDeviceArm64 requires Kotlin 1.8 or greater")
            }
            holder.configure(
                targetName,
                ::DeviceArm64,
                KmpTargetCode.WATCHOS_DEVICE_ARM64,
                configure,
            )
        }

        override fun watchosX64(
            targetName: String,
            configure: AppleWatchosTarget<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::X64, KmpTargetCode.WATCHOS_X64, configure)
        }

        override fun watchosSimulatorArm64(
            targetName: String,
            configure: AppleWatchosTarget<KNTS>.() -> Unit,
        ) {
            holder.configure(targetName, ::SimulatorArm64, WATCHOS_SIMULATOR_ARM64, configure)
        }
    }


    class Arm32(context: ContainerContext, targetName: String) :
        TargetAppleWatchosContainer<KNT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::watchosArm32)
    }

    class Arm64(context: ContainerContext, targetName: String) :
        TargetAppleWatchosContainer<KNT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::watchosArm64)
    }

    class DeviceArm64(context: ContainerContext, targetName: String) :
        TargetAppleWatchosContainer<KNT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::watchosDeviceArm64)
    }

    class X64(context: ContainerContext, targetName: String) :
        TargetAppleWatchosContainer<KNTS>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::watchosX64)
    }

    class SimulatorArm64(context: ContainerContext, targetName: String) :
        TargetAppleWatchosContainer<KNTS>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() =
            createTarget(::watchosSimulatorArm64)
    }
}
