package fluxo.conf.dsl.container.impl.target

import fluxo.conf.dsl.container.impl.ContainerContext
import fluxo.conf.dsl.container.impl.ContainerHolderAware
import fluxo.conf.dsl.container.impl.KmpTargetCode
import fluxo.conf.dsl.container.impl.KmpTargetCode.Companion.DEPRECATED_TARGET_MSG
import fluxo.conf.dsl.container.impl.KmpTargetContainerImpl
import fluxo.conf.dsl.container.target.LinuxTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget as KNT
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTargetWithHostTests as KNTHT

internal abstract class TargetLinuxContainer<T : KNT>(
    context: ContainerContext, name: String,
) : KmpTargetContainerImpl<T>(context, name, LINUX_SORT_ORDER),
    KmpTargetContainerImpl.NonJvm.Native.Unix.Linux<T>, LinuxTarget<T> {

    interface Configure : LinuxTarget.Configure, ContainerHolderAware {

        override fun linuxX64(targetName: String, configure: LinuxTarget<KNTHT>.() -> Unit) {
            holder.configure(targetName, ::X64, KmpTargetCode.LINUX_X64, configure)
        }

        override fun linuxArm64(targetName: String, configure: LinuxTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm64, KmpTargetCode.LINUX_ARM64, configure)
        }


        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun linuxArm32Hfp(targetName: String, configure: LinuxTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Arm32Hfp, KmpTargetCode.LINUX_ARM32_HFP, configure)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun linuxMips32(targetName: String, configure: LinuxTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Mips32, KmpTargetCode.LINUX_MIPS32, configure)
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun linuxMipsel32(targetName: String, configure: LinuxTarget<KNT>.() -> Unit) {
            holder.configure(targetName, ::Mipsel32, KmpTargetCode.LINUX_MIPSEL32, configure)
        }
    }


    class X64(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNTHT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxX64)
    }

    class Arm64(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName) {

        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxArm64)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class Arm32Hfp(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName) {

        @Suppress("DEPRECATION")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxArm32Hfp)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class Mips32(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName) {

        @Suppress("DEPRECATION", "DEPRECATION_ERROR", "KotlinRedundantDiagnosticSuppress")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxMips32)
    }

    @Deprecated(DEPRECATED_TARGET_MSG)
    class Mipsel32(context: ContainerContext, targetName: String) :
        TargetLinuxContainer<KNT>(context, targetName) {

        @Suppress("DEPRECATION", "DEPRECATION_ERROR", "KotlinRedundantDiagnosticSuppress")
        override fun KotlinMultiplatformExtension.createTarget() = createTarget(::linuxMipsel32)
    }
}
